package com.studydeck.infrastructure.adapter.out.persistence;

import com.studydeck.domain.model.EmbeddingRecord;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.out.EmbeddingRecordRepository;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/** JPA-backed implementation of {@link EmbeddingRecordRepository}. */
@Transactional
class EmbeddingRecordPersistenceAdapter implements EmbeddingRecordRepository {

  private final EmbeddingRecordJpaRepository jpaRepo;
  private final CorpusPersistenceMapper mapper;

  EmbeddingRecordPersistenceAdapter(
      EmbeddingRecordJpaRepository jpaRepo, CorpusPersistenceMapper mapper) {
    this.jpaRepo = jpaRepo;
    this.mapper = mapper;
  }

  @Override
  public void save(EmbeddingRecord record) {
    jpaRepo.save(mapper.toJpa(record));
  }

  @Override
  @Transactional(readOnly = true)
  public List<EmbeddingRecord> findByOwner(OwnerId ownerId, int offset, int limit) {
    return jpaRepo.findByOwnerIdOrderByCreatedAtDesc(ownerId.value()).stream()
        .skip(offset)
        .limit(limit)
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long countByOwner(OwnerId ownerId) {
    return jpaRepo.countByOwnerId(ownerId.value());
  }
}
