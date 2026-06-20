package com.studydeck.infrastructure.adapter.out.persistence;

import com.studydeck.domain.model.IngestJob;
import com.studydeck.domain.model.IngestJobId;
import com.studydeck.domain.port.out.IngestJobRepository2;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/** JPA-backed implementation of {@link IngestJobRepository2}. */
@Transactional
class IngestJobPersistenceAdapter2 implements IngestJobRepository2 {

  private final IngestJobJpaRepository2 jpaRepo;
  private final CorpusPersistenceMapper mapper;

  IngestJobPersistenceAdapter2(IngestJobJpaRepository2 jpaRepo, CorpusPersistenceMapper mapper) {
    this.jpaRepo = jpaRepo;
    this.mapper = mapper;
  }

  @Override
  public void save(IngestJob job) {
    jpaRepo.save(mapper.toJpa(job));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<IngestJob> findById(IngestJobId id) {
    return jpaRepo.findById(id.value()).map(mapper::toDomain);
  }
}
