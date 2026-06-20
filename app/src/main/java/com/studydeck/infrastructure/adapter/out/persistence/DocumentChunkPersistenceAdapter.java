package com.studydeck.infrastructure.adapter.out.persistence;

import com.studydeck.domain.model.ChunkId;
import com.studydeck.domain.model.DocumentChunk;
import com.studydeck.domain.model.DocumentId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.out.DocumentChunkRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/** JPA-backed implementation of {@link DocumentChunkRepository}. */
@Transactional
class DocumentChunkPersistenceAdapter implements DocumentChunkRepository {

  private final DocumentChunkJpaRepository jpaRepo;
  private final CorpusPersistenceMapper mapper;

  DocumentChunkPersistenceAdapter(
      DocumentChunkJpaRepository jpaRepo, CorpusPersistenceMapper mapper) {
    this.jpaRepo = jpaRepo;
    this.mapper = mapper;
  }

  @Override
  public void save(DocumentChunk chunk) {
    jpaRepo.save(mapper.toJpa(chunk));
  }

  @Override
  public void saveAll(List<DocumentChunk> chunks) {
    jpaRepo.saveAll(chunks.stream().map(mapper::toJpa).toList());
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<DocumentChunk> findById(ChunkId id) {
    return jpaRepo.findById(id.value()).map(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<DocumentChunk> findByDocumentId(DocumentId documentId, int offset, int limit) {
    return jpaRepo.findByDocumentIdOrderByOrdinal(documentId.value()).stream()
        .skip(offset)
        .limit(limit)
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long countByDocumentId(DocumentId documentId) {
    return jpaRepo.countByDocumentId(documentId.value());
  }

  @Override
  @Transactional(readOnly = true)
  public List<DocumentChunk> findByOwner(OwnerId ownerId, String search, int offset, int limit) {
    String searchParam = (search == null || search.isBlank()) ? null : search;
    return jpaRepo.findByOwnerWithSearch(ownerId.value(), searchParam).stream()
        .skip(offset)
        .limit(limit)
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long countByOwner(OwnerId ownerId, String search) {
    String searchParam = (search == null || search.isBlank()) ? null : search;
    return jpaRepo.countByOwnerWithSearch(ownerId.value(), searchParam);
  }

  @Override
  public void deleteByDocumentId(DocumentId documentId) {
    jpaRepo.deleteByDocumentId(documentId.value());
  }
}
