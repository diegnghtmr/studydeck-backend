package com.studydeck.application.service;

import com.studydeck.application.exception.NotFoundException;
import com.studydeck.domain.model.DocumentId;
import com.studydeck.domain.model.IngestJob;
import com.studydeck.domain.model.IngestJobId;
import com.studydeck.domain.model.IngestStatus;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.SourceDocument;
import com.studydeck.domain.port.in.CreateDocumentUseCase;
import com.studydeck.domain.port.in.IngestDocumentUseCase;
import com.studydeck.domain.port.in.ListDocumentsQuery;
import com.studydeck.domain.port.out.AsyncIngestPort;
import com.studydeck.domain.port.out.ClockPort;
import com.studydeck.domain.port.out.IdGenerator;
import com.studydeck.domain.port.out.IngestJobRepository2;
import com.studydeck.domain.port.out.SourceDocumentRepository;
import java.util.Map;

/**
 * Application service implementing document WRITE use cases and list query.
 *
 * <p>GetDocumentQuery and DeleteDocumentUseCase are separate services due to return-type collision
 * in their {@code execute(DocumentId, OwnerId)} signatures.
 *
 * <p>Framework-free: no Spring annotations. Wired as {@code @Bean} in {@link
 * com.studydeck.infrastructure.config.AiConfiguration}.
 */
public final class DocumentService
    implements CreateDocumentUseCase, ListDocumentsQuery, IngestDocumentUseCase {

  private final SourceDocumentRepository documentRepository;
  private final IngestJobRepository2 ingestJobRepository;
  private final IdGenerator idGenerator;
  private final ClockPort clock;
  private final AsyncIngestPort asyncIngestPort;

  public DocumentService(
      SourceDocumentRepository documentRepository,
      IngestJobRepository2 ingestJobRepository,
      IdGenerator idGenerator,
      ClockPort clock,
      AsyncIngestPort asyncIngestPort) {
    this.documentRepository = documentRepository;
    this.ingestJobRepository = ingestJobRepository;
    this.idGenerator = idGenerator;
    this.clock = clock;
    this.asyncIngestPort = asyncIngestPort;
  }

  // ---------------------------------------------------------------
  // CreateDocumentUseCase
  // ---------------------------------------------------------------

  @Override
  public SourceDocument execute(CreateDocumentUseCase.Command command) {
    var id = new DocumentId(idGenerator.generate());
    var now = clock.now();
    var doc =
        SourceDocument.create(
            id,
            command.ownerId(),
            command.title(),
            command.sourceType(),
            command.mimeType(),
            command.originalFilename(),
            command.textContent(),
            command.externalUrl(),
            command.sizeBytes(),
            command.metadata() != null ? command.metadata() : Map.of(),
            now);
    documentRepository.save(doc);
    return doc;
  }

  // ---------------------------------------------------------------
  // ListDocumentsQuery
  // ---------------------------------------------------------------

  @Override
  public ListDocumentsQuery.Result execute(
      OwnerId ownerId, IngestStatus ingestStatus, int offset, int limit) {
    var items = documentRepository.findAll(ownerId, ingestStatus, offset, limit);
    var total = documentRepository.countAll(ownerId, ingestStatus);
    return new ListDocumentsQuery.Result(items, total);
  }

  // ---------------------------------------------------------------
  // IngestDocumentUseCase
  // ---------------------------------------------------------------

  @Override
  public IngestJob execute(IngestDocumentUseCase.Command command) {
    var doc =
        documentRepository
            .findById(command.documentId())
            .orElseThrow(() -> new NotFoundException("Document", command.documentId().toString()));
    if (!doc.getOwnerId().equals(command.ownerId())) {
      throw new NotFoundException("Document", command.documentId().toString());
    }
    var jobId = new IngestJobId(idGenerator.generate());
    var now = clock.now();
    var job = IngestJob.create(jobId, command.documentId(), command.ownerId(), now);
    ingestJobRepository.save(job);
    // Trigger async ETL via the outbound port; returns immediately with PENDING job.
    asyncIngestPort.startIngest(
        jobId.value().toString(),
        command.documentId().value().toString(),
        command.ownerId().value().toString(),
        command.targetChunkSize(),
        command.chunkOverlap());
    return job;
  }
}
