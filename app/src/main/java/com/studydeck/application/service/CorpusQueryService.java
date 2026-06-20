package com.studydeck.application.service;

import com.studydeck.application.exception.NotFoundException;
import com.studydeck.domain.model.DocumentId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.ListChunksQuery;
import com.studydeck.domain.port.in.ListDocumentChunksQuery;
import com.studydeck.domain.port.in.ListEmbeddingsQuery;
import com.studydeck.domain.port.out.DocumentChunkRepository;
import com.studydeck.domain.port.out.EmbeddingRecordRepository;
import com.studydeck.domain.port.out.SourceDocumentRepository;

/**
 * Application service for corpus query use cases (chunks + embeddings listing).
 *
 * <p>Framework-free: no Spring annotations. Wired as {@code @Bean} in {@link
 * com.studydeck.infrastructure.config.AiConfiguration}.
 */
public final class CorpusQueryService
    implements ListDocumentChunksQuery, ListChunksQuery, ListEmbeddingsQuery {

  private final SourceDocumentRepository documentRepository;
  private final DocumentChunkRepository chunkRepository;
  private final EmbeddingRecordRepository embeddingRepository;

  public CorpusQueryService(
      SourceDocumentRepository documentRepository,
      DocumentChunkRepository chunkRepository,
      EmbeddingRecordRepository embeddingRepository) {
    this.documentRepository = documentRepository;
    this.chunkRepository = chunkRepository;
    this.embeddingRepository = embeddingRepository;
  }

  // ---------------------------------------------------------------
  // ListDocumentChunksQuery
  // ---------------------------------------------------------------

  @Override
  public ListDocumentChunksQuery.Result execute(
      DocumentId documentId, OwnerId ownerId, int offset, int limit) {
    var doc =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new NotFoundException("Document", documentId.toString()));
    if (!doc.getOwnerId().equals(ownerId)) {
      throw new NotFoundException("Document", documentId.toString());
    }
    var items = chunkRepository.findByDocumentId(documentId, offset, limit);
    var total = chunkRepository.countByDocumentId(documentId);
    return new ListDocumentChunksQuery.Result(items, total);
  }

  // ---------------------------------------------------------------
  // ListChunksQuery
  // ---------------------------------------------------------------

  @Override
  public ListChunksQuery.Result execute(OwnerId ownerId, String search, int offset, int limit) {
    var items = chunkRepository.findByOwner(ownerId, search, offset, limit);
    var total = chunkRepository.countByOwner(ownerId, search);
    return new ListChunksQuery.Result(items, total);
  }

  // ---------------------------------------------------------------
  // ListEmbeddingsQuery
  // ---------------------------------------------------------------

  @Override
  public ListEmbeddingsQuery.Result execute(OwnerId ownerId, int offset, int limit) {
    var items = embeddingRepository.findByOwner(ownerId, offset, limit);
    var total = embeddingRepository.countByOwner(ownerId);
    return new ListEmbeddingsQuery.Result(items, total);
  }
}
