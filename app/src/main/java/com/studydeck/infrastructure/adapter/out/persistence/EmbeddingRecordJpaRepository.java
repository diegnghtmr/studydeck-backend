package com.studydeck.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link EmbeddingRecordJpaEntity}. */
interface EmbeddingRecordJpaRepository extends JpaRepository<EmbeddingRecordJpaEntity, UUID> {

  List<EmbeddingRecordJpaEntity> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

  long countByOwnerId(UUID ownerId);
}
