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

  /** Counts cards with due_at <= now for this owner across ALL decks. */
  @Query(
      value =
          "SELECT COUNT(*) FROM card_schedule_state "
              + "WHERE owner_id = :ownerId AND due_at <= :now",
      nativeQuery = true)
  long countDueGlobal(@Param("ownerId") UUID ownerId, @Param("now") Instant now);

  /**
   * Counts cards belonging to this owner that are either: (a) in NEW state in card_schedule_state,
   * or (b) owned cards with NO schedule row at all.
   */
  @Query(
      value =
          "SELECT COUNT(*) FROM card c "
              + "JOIN note n ON n.id = c.note_id "
              + "JOIN deck d ON d.id = n.deck_id "
              + "LEFT JOIN card_schedule_state css ON css.card_id = c.id "
              + "WHERE d.owner_id = :ownerId "
              + "AND c.suspended = FALSE "
              + "AND (css.card_id IS NULL OR css.state = 'NEW')",
      nativeQuery = true)
  long countNewGlobal(@Param("ownerId") UUID ownerId);

  /**
   * Counts NEW (or unscheduled) cards for this owner within a single deck. Same semantics as {@link
   * #countNewGlobal(UUID)} scoped to one deck.
   */
  @Query(
      value =
          "SELECT COUNT(*) FROM card c "
              + "JOIN note n ON n.id = c.note_id "
              + "JOIN deck d ON d.id = n.deck_id "
              + "LEFT JOIN card_schedule_state css ON css.card_id = c.id "
              + "WHERE d.owner_id = :ownerId "
              + "AND d.id = :deckId "
              + "AND c.suspended = FALSE "
              + "AND (css.card_id IS NULL OR css.state = 'NEW')",
      nativeQuery = true)
  long countNewByDeck(@Param("ownerId") UUID ownerId, @Param("deckId") UUID deckId);
}
