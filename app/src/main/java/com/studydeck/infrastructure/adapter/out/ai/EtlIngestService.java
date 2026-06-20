package com.studydeck.infrastructure.adapter.out.ai;

import com.studydeck.domain.model.ChunkId;
import com.studydeck.domain.model.DocumentChunk;
import com.studydeck.domain.model.EmbeddingRecord;
import com.studydeck.domain.model.EmbeddingRecordId;
import com.studydeck.domain.model.IngestJob;
import com.studydeck.domain.model.IngestJobId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.SourceDocument;
import com.studydeck.domain.port.out.AsyncIngestPort;
import com.studydeck.domain.port.out.ClockPort;
import com.studydeck.domain.port.out.DocumentChunkRepository;
import com.studydeck.domain.port.out.EmbeddingRecordRepository;
import com.studydeck.domain.port.out.IdGenerator;
import com.studydeck.domain.port.out.IngestJobRepository2;
import com.studydeck.domain.port.out.SourceDocumentRepository;
import com.studydeck.domain.port.out.TextEmbeddingPort;
import com.studydeck.domain.port.out.TextEmbeddingPort.ChunkToEmbed;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring-managed ETL service that runs ingest asynchronously.
 *
 * <p>This class is in the infrastructure layer and may use Spring annotations (unlike domain/app
 * layer). It is triggered by {@link com.studydeck.application.service.DocumentService} after the
 * 202 response is returned, and processes the actual ETL pipeline:
 *
 * <ol>
 *   <li>Load the source document text
 *   <li>Split into chunks using Spring AI's TokenTextSplitter
 *   <li>Persist chunks to document_chunk table
 *   <li>Embed chunks and store to pgvector vector_store
 *   <li>Persist embedding metadata to embedding_record table
 *   <li>Update IngestJob and SourceDocument status
 * </ol>
 *
 * <p>This is the ONLY place that imports Spring AI types directly.
 */
@Service
public class EtlIngestService implements AsyncIngestPort {

  private static final Logger log = LoggerFactory.getLogger(EtlIngestService.class);

  private final SourceDocumentRepository documentRepository;
  private final DocumentChunkRepository chunkRepository;
  private final EmbeddingRecordRepository embeddingRepository;
  private final IngestJobRepository2 ingestJobRepository;
  private final TextEmbeddingPort embeddingPort;
  private final IdGenerator idGenerator;
  private final ClockPort clock;

  EtlIngestService(
      SourceDocumentRepository documentRepository,
      DocumentChunkRepository chunkRepository,
      EmbeddingRecordRepository embeddingRepository,
      IngestJobRepository2 ingestJobRepository,
      TextEmbeddingPort embeddingPort,
      IdGenerator idGenerator,
      ClockPort clock) {
    this.documentRepository = documentRepository;
    this.chunkRepository = chunkRepository;
    this.embeddingRepository = embeddingRepository;
    this.ingestJobRepository = ingestJobRepository;
    this.embeddingPort = embeddingPort;
    this.idGenerator = idGenerator;
    this.clock = clock;
  }

  /**
   * Runs the ETL pipeline asynchronously for a given ingest job.
   *
   * @param jobId the ingest job UUID string
   * @param documentId the source document UUID string
   * @param ownerId the owner UUID string
   * @param targetChunkSize target token count per chunk (default 400)
   * @param chunkOverlap token overlap between chunks (default 40)
   */
  @Override
  @Async
  @Transactional
  public void startIngest(
      String jobId, String documentId, String ownerId, int targetChunkSize, int chunkOverlap) {

    var jobIdObj = new IngestJobId(java.util.UUID.fromString(jobId));
    var docIdObj = new com.studydeck.domain.model.DocumentId(java.util.UUID.fromString(documentId));
    var ownerIdObj = new OwnerId(java.util.UUID.fromString(ownerId));
    var now = clock.now();

    // Load entities
    var jobOpt = ingestJobRepository.findById(jobIdObj);
    if (jobOpt.isEmpty()) {
      log.warn("ETL: ingest job {} not found, skipping", jobId);
      return;
    }
    IngestJob job = jobOpt.get();

    var docOpt = documentRepository.findById(docIdObj);
    if (docOpt.isEmpty()) {
      log.warn("ETL: document {} not found, failing job {}", documentId, jobId);
      job.markFailed("Document not found", now);
      ingestJobRepository.save(job);
      return;
    }
    SourceDocument doc = docOpt.get();

    // Mark running
    job.markRunning(now);
    ingestJobRepository.save(job);
    doc.markRunning(now);
    documentRepository.save(doc);

    try {
      String text = doc.getTextContent();
      if (text == null || text.isBlank()) {
        throw new IllegalStateException("Document has no text content to ingest");
      }

      // 1. Split text into chunks using Spring AI TokenTextSplitter
      var splitter =
          TokenTextSplitter.builder()
              .withChunkSize(targetChunkSize)
              .withMinChunkSizeChars(50)
              .withMinChunkLengthToEmbed(10)
              .withMaxNumChunks(5000)
              .withKeepSeparator(true)
              .build();

      var springAiDoc =
          new Document(
              text,
              Map.of(
                  "ownerId",
                  ownerId,
                  "documentId",
                  documentId,
                  "title",
                  doc.getTitle(),
                  "sourceType",
                  doc.getSourceType()));
      List<Document> splitDocs = splitter.apply(List.of(springAiDoc));

      // 2. Persist domain DocumentChunk rows
      List<DocumentChunk> domainChunks = new ArrayList<>();
      int ordinal = 0;
      for (Document splitDoc : splitDocs) {
        var chunkId = new ChunkId(idGenerator.generate());
        var chunk =
            DocumentChunk.create(
                chunkId,
                docIdObj,
                ownerIdObj,
                ordinal++,
                splitDoc.getText(),
                null, // tokenCount: not easily available from splitter without extra work
                Map.of(
                    "ownerId", ownerId,
                    "documentId", documentId,
                    "sourceType", doc.getSourceType()),
                now);
        domainChunks.add(chunk);
      }
      chunkRepository.saveAll(domainChunks);

      // 3. Embed and store in pgvector
      List<ChunkToEmbed> chunksToEmbed =
          domainChunks.stream()
              .map(
                  c ->
                      new ChunkToEmbed(
                          c.getId(),
                          docIdObj,
                          ownerIdObj,
                          c.getContent(),
                          Map.of(
                              "ownerId",
                              ownerId,
                              "documentId",
                              documentId,
                              "chunkId",
                              c.getId().toString(),
                              "sourceType",
                              doc.getSourceType())))
              .toList();
      embeddingPort.embedAndStore(chunksToEmbed);

      // 4. Persist embedding metadata
      for (DocumentChunk chunk : domainChunks) {
        var embId = new EmbeddingRecordId(idGenerator.generate());
        var emb =
            EmbeddingRecord.create(
                embId,
                chunk.getId(),
                ownerIdObj,
                embeddingPort.modelName(),
                embeddingPort.dimensions(),
                "transformers",
                now);
        embeddingRepository.save(emb);
      }

      // 5. Mark completed
      var finishNow = clock.now();
      job.markCompleted(domainChunks.size(), finishNow);
      ingestJobRepository.save(job);
      doc.markCompleted(finishNow);
      documentRepository.save(doc);

      log.info(
          "ETL: completed ingest job {} — {} chunks produced for document {}",
          jobId,
          domainChunks.size(),
          documentId);

    } catch (Exception ex) {
      log.error("ETL: ingest job {} failed: {}", jobId, ex.getMessage(), ex);
      var failNow = clock.now();
      job.markFailed(ex.getMessage(), failNow);
      ingestJobRepository.save(job);
      doc.markFailed(failNow);
      documentRepository.save(doc);
    }
  }
}
