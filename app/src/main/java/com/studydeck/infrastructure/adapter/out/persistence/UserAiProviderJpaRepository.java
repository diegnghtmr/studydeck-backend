package com.studydeck.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@link UserAiProviderJpaEntity}. */
interface UserAiProviderJpaRepository extends JpaRepository<UserAiProviderJpaEntity, UUID> {

  Optional<UserAiProviderJpaEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

  List<UserAiProviderJpaEntity> findAllByOwnerId(UUID ownerId);

  Optional<UserAiProviderJpaEntity> findByOwnerIdAndActiveTrue(UUID ownerId);

  void deleteByIdAndOwnerId(UUID id, UUID ownerId);

  /** Sets {@code active = false} for all providers belonging to the given owner. */
  @Modifying
  @Query("UPDATE UserAiProviderJpaEntity e SET e.active = false WHERE e.ownerId = :ownerId")
  void deactivateAllByOwnerId(@Param("ownerId") UUID ownerId);
}
