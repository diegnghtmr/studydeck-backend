package com.studydeck.domain.port.out;

import com.studydeck.domain.model.ChunkId;
import com.studydeck.domain.model.DocumentId;
import com.studydeck.domain.model.OwnerId;
import java.util.List;
import java.util.Map;

/**
 * Output port for embedding text chunks and performing vector similarity search.
 *
 * <p>Implemented by the Spring AI pgvector adapter. The domain sees an abstract embedding/search
 * capability — it never depends on Spring AI types directly.
 */
public interface TextEmbeddingPort {

  /** Returns the embedding model name (e.g. "all-MiniLM-L6-v2"). */
  String modelName();

  /** Returns the embedding vector dimension (e.g. 384). */
  int dimensions();

  /**
   * Embeds a list of text chunks and stores them in the vector store with metadata.
   *
   * <p>Metadata MUST include ownerId and documentId for owner-scoped retrieval.
   */
  void embedAndStore(List<ChunkToEmbed> chunks);

  /**
   * Searches the vector store for chunks similar to the query, filtered by ownerId.
   *
   * @param query the natural language query
   * @param ownerId restricts results to the owner's corpus only (security boundary)
   * @param documentIds optional filter by specific documents (empty = all owner's docs)
   * @param topK number of top results
   * @param minScore similarity score threshold (0.0–1.0), null = no threshold
   * @return ranked list of search hits
   */
  List<SearchHit> search(
      String query, OwnerId ownerId, List<DocumentId> documentIds, int topK, Double minScore);

  /** Deletes all vector store entries for a document (called when document is deleted). */
  void deleteByDocumentId(DocumentId documentId, OwnerId ownerId);

  // ---------------------------------------------------------------
  // Value types (domain-safe: no Spring AI imports)
  // ---------------------------------------------------------------

  record ChunkToEmbed(
      ChunkId chunkId,
      DocumentId documentId,
      OwnerId ownerId,
      String content,
      Map<String, Object> extraMetadata) {}

  record SearchHit(
      ChunkId chunkId,
      DocumentId documentId,
      double score,
      String content,
      Map<String, Object> metadata) {}
}
