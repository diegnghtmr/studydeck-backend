package com.studydeck.infrastructure.adapter.out.persistence;

import com.studydeck.domain.model.DocumentId;
import com.studydeck.domain.model.IngestStatus;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.SourceDocument;
import com.studydeck.domain.port.out.SourceDocumentRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/** JPA-backed implementation of {@link SourceDocumentRepository}. */
@Transactional
class SourceDocumentPersistenceAdapter implements SourceDocumentRepository {

  private final SourceDocumentJpaRepository jpaRepo;
  private final CorpusPersistenceMapper mapper;

  SourceDocumentPersistenceAdapter(
      SourceDocumentJpaRepository jpaRepo, CorpusPersistenceMapper mapper) {
    this.jpaRepo = jpaRepo;
    this.mapper = mapper;
  }

  @Override
  public void save(SourceDocument document) {
    jpaRepo.save(mapper.toJpa(document));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<SourceDocument> findById(DocumentId id) {
    return jpaRepo.findById(id.value()).map(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<SourceDocument> findAll(
      OwnerId ownerId, IngestStatus ingestStatus, int offset, int limit) {
    String statusStr = (ingestStatus != null) ? ingestStatus.name() : null;
    return jpaRepo.findByOwnerWithFilter(ownerId.value(), statusStr).stream()
        .skip(offset)
        .limit(limit)
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long countAll(OwnerId ownerId, IngestStatus ingestStatus) {
    String statusStr = (ingestStatus != null) ? ingestStatus.name() : null;
    return jpaRepo.countByOwnerWithFilter(ownerId.value(), statusStr);
  }

  @Override
  public void deleteById(DocumentId id) {
    jpaRepo.deleteById(id.value());
  }
}
