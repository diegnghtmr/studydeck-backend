package com.studydeck.infrastructure.adapter.out.ai;

import com.studydeck.domain.model.ChunkId;
import com.studydeck.domain.model.DocumentId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.out.TextEmbeddingPort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * Spring AI adapter implementing {@link TextEmbeddingPort}.
 *
 * <p>Uses Spring AI's {@link VectorStore} (backed by pgvector) for embedding storage and semantic
 * search. Owner-scoping is enforced via metadata filter on {@code ownerId}.
 *
 * <p>This class lives in infrastructure — the only place allowed to import Spring AI types.
 */
public class SpringAiEmbeddingAdapter implements TextEmbeddingPort {

  /** Model name for all-MiniLM-L6-v2 (default ONNX Transformers model). */
  public static final String DEFAULT_MODEL = "all-MiniLM-L6-v2";

  /** Embedding dimension for all-MiniLM-L6-v2. */
  public static final int DEFAULT_DIMENSIONS = 384;

  private final VectorStore vectorStore;
  private final String modelName;
  private final int dimensions;

  public SpringAiEmbeddingAdapter(VectorStore vectorStore, String modelName, int dimensions) {
    this.vectorStore = vectorStore;
    this.modelName = modelName;
    this.dimensions = dimensions;
  }

  @Override
  public String modelName() {
    return modelName;
  }

  @Override
  public int dimensions() {
    return dimensions;
  }

  @Override
  public void embedAndStore(List<ChunkToEmbed> chunks) {
    if (chunks.isEmpty()) return;

    List<Document> docs = new ArrayList<>(chunks.size());
    for (ChunkToEmbed chunk : chunks) {
      Map<String, Object> metadata = new HashMap<>();
      metadata.put("ownerId", chunk.ownerId().value().toString());
      metadata.put("documentId", chunk.documentId().value().toString());
      metadata.put("chunkId", chunk.chunkId().value().toString());
      if (chunk.extraMetadata() != null) {
        chunk
            .extraMetadata()
            .forEach((k, v) -> metadata.merge(k, v, (existing, incoming) -> incoming));
      }
      // Use chunkId as the Spring AI document ID so we can correlate later
      docs.add(new Document(chunk.chunkId().value().toString(), chunk.content(), metadata));
    }
    vectorStore.add(docs);
  }

  @Override
  public List<SearchHit> search(
      String query, OwnerId ownerId, List<DocumentId> documentIds, int topK, Double minScore) {

    // Build filter expression: ownerId MUST match (security boundary).
    // Uses string form — supported natively by Spring AI 2.x SearchRequest.
    String ownerFilter = "ownerId == '" + ownerId.value() + "'";

    var requestBuilder =
        SearchRequest.builder().query(query).topK(topK).filterExpression(ownerFilter);

    if (minScore != null) {
      requestBuilder.similarityThreshold(minScore);
    }

    var request = requestBuilder.build();

    List<Document> results = vectorStore.similaritySearch(request);

    return results.stream()
        .map(
            doc -> {
              var meta = doc.getMetadata();
              String chunkIdStr = (String) meta.get("chunkId");
              String docIdStr = (String) meta.get("documentId");
              double score = doc.getScore() != null ? doc.getScore() : 0.0;

              ChunkId chunkId =
                  chunkIdStr != null
                      ? new ChunkId(UUID.fromString(chunkIdStr))
                      : new ChunkId(UUID.fromString(doc.getId()));
              DocumentId documentId =
                  docIdStr != null
                      ? new DocumentId(UUID.fromString(docIdStr))
                      : new DocumentId(UUID.randomUUID());

              return new SearchHit(chunkId, documentId, score, doc.getText(), meta);
            })
        .filter(hit -> documentIds.isEmpty() || documentIds.contains(hit.documentId()))
        .toList();
  }

  @Override
  public void deleteByDocumentId(DocumentId documentId, OwnerId ownerId) {
    // Delete by filter using SQL-style string — simpler than building Filter.Expression manually
    // and supported natively by Spring AI 2.x VectorStore.delete(String).
    String filterExpression =
        "ownerId == '" + ownerId.value() + "' && documentId == '" + documentId.value() + "'";
    vectorStore.delete(filterExpression);
  }
}
