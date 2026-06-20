package com.studydeck.infrastructure.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@link ReviewSessionJpaEntity}. */
interface ReviewSessionJpaRepository extends JpaRepository<ReviewSessionJpaEntity, UUID> {

  @Modifying
  @Query(
      "UPDATE ReviewSessionJpaEntity s SET s.presentedCount = s.presentedCount + 1 WHERE s.id = :id")
  void incrementPresentedCount(@Param("id") UUID id);

  @Modifying
  @Query(
      "UPDATE ReviewSessionJpaEntity s SET s.answeredCount = s.answeredCount + 1 WHERE s.id = :id")
  void incrementAnsweredCount(@Param("id") UUID id);
}
