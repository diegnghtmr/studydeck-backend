package com.studydeck.infrastructure.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link AuditEventJpaEntity}. */
interface AuditEventJpaRepository extends JpaRepository<AuditEventJpaEntity, UUID> {}
