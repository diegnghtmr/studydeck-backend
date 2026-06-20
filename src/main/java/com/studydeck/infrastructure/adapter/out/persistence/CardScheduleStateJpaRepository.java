package com.studydeck.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@link CardScheduleStateJpaEntity}. */
interface CardScheduleStateJpaRepository extends JpaRepository<CardScheduleStateJpaEntity, UUID> {

  /**
   * Finds card IDs that are due for a given owner, optionally filtered by deck, ordered by due_at
   * ascending, limited to {@code limit} results.
   */
  @Query(
      value =
          "SELECT css.card_id FROM card_schedule_state css "
              + "WHERE css.owner_id = :ownerId "
              + "AND (:deckId IS NULL OR css.deck_id = :deckId) "
              + "AND css.due_at <= :dueAt "
              + "ORDER BY css.due_at ASC "
              + "LIMIT :limit",
      nativeQuery = true)
  List<UUID> findDueCardIds(
      @Param("ownerId") UUID ownerId,
      @Param("deckId") UUID deckId,
      @Param("dueAt") Instant dueAt,
      @Param("limit") int limit);
}
