package com.studydeck.infrastructure.adapter.out.persistence;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.out.AuditEventPort;
import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** JPA-backed implementation of {@link AuditEventPort}. */
@Transactional
class AuditEventPersistenceAdapter implements AuditEventPort {

  private final AuditEventJpaRepository jpaRepo;

  AuditEventPersistenceAdapter(AuditEventJpaRepository jpaRepo) {
    this.jpaRepo = jpaRepo;
  }

  @Override
  public void record(OwnerId actorId, String action, String targetType, String targetId) {
    AuditEventJpaEntity e = new AuditEventJpaEntity();
    e.setId(UUID.randomUUID());
    e.setActorId(actorId.value());
    e.setAction(action);
    e.setTargetType(targetType);
    e.setTargetId(targetId);
    e.setOccurredAt(Instant.now());
    jpaRepo.save(e);
  }
}
