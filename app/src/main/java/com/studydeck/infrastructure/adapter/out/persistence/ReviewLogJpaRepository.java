package com.studydeck.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@link ReviewLogJpaEntity}. */
interface ReviewLogJpaRepository extends JpaRepository<ReviewLogJpaEntity, UUID> {

  @Query(
      value =
          "SELECT rl.* FROM review_log rl "
              + "JOIN card c ON c.id = rl.card_id "
              + "JOIN note n ON n.id = c.note_id "
              + "WHERE rl.owner_id = :ownerId "
              + "AND (CAST(:deckId AS uuid) IS NULL OR rl.deck_id = CAST(:deckId AS uuid)) "
              + "AND (CAST(:cardId AS uuid) IS NULL OR rl.card_id = CAST(:cardId AS uuid)) "
              + "AND (CAST(:from AS timestamptz) IS NULL OR rl.reviewed_at >= CAST(:from AS timestamptz)) "
              + "AND (CAST(:to AS timestamptz) IS NULL OR rl.reviewed_at <= CAST(:to AS timestamptz)) "
              + "ORDER BY rl.reviewed_at DESC "
              + "OFFSET :offset LIMIT :limit",
      nativeQuery = true)
  List<ReviewLogJpaEntity> findHistory(
      @Param("ownerId") UUID ownerId,
      @Param("deckId") UUID deckId,
      @Param("cardId") UUID cardId,
      @Param("from") Instant from,
      @Param("to") Instant to,
      @Param("offset") int offset,
      @Param("limit") int limit);

  @Query(
      value =
          "SELECT COUNT(*) FROM review_log rl "
              + "WHERE rl.owner_id = :ownerId "
              + "AND (CAST(:deckId AS uuid) IS NULL OR rl.deck_id = CAST(:deckId AS uuid)) "
              + "AND (CAST(:cardId AS uuid) IS NULL OR rl.card_id = CAST(:cardId AS uuid)) "
              + "AND (CAST(:from AS timestamptz) IS NULL OR rl.reviewed_at >= CAST(:from AS timestamptz)) "
              + "AND (CAST(:to AS timestamptz) IS NULL OR rl.reviewed_at <= CAST(:to AS timestamptz))",
      nativeQuery = true)
  long countHistory(
      @Param("ownerId") UUID ownerId,
      @Param("deckId") UUID deckId,
      @Param("cardId") UUID cardId,
      @Param("from") Instant from,
      @Param("to") Instant to);

  @Query(
      value =
          "SELECT COUNT(*) FROM review_log rl "
              + "WHERE rl.owner_id = :ownerId "
              + "AND rl.deck_id = :deckId "
              + "AND rl.reviewed_at >= :dayStart "
              + "AND rl.reviewed_at < :dayEnd",
      nativeQuery = true)
  int countReviewedToday(
      @Param("ownerId") UUID ownerId,
      @Param("deckId") UUID deckId,
      @Param("dayStart") Instant dayStart,
      @Param("dayEnd") Instant dayEnd);

  @Query(
      value =
          "SELECT CAST(SUM(CASE WHEN rating = 'AGAIN' THEN 1 ELSE 0 END) AS DOUBLE PRECISION) / NULLIF(COUNT(*), 0) "
              + "FROM review_log "
              + "WHERE owner_id = :ownerId AND deck_id = :deckId AND reviewed_at >= :since",
      nativeQuery = true)
  Double againRate7d(
      @Param("ownerId") UUID ownerId, @Param("deckId") UUID deckId, @Param("since") Instant since);

  @Query(
      value =
          "SELECT CAST(SUM(CASE WHEN rating != 'AGAIN' THEN 1 ELSE 0 END) AS DOUBLE PRECISION) / NULLIF(COUNT(*), 0) "
              + "FROM review_log "
              + "WHERE owner_id = :ownerId AND deck_id = :deckId AND reviewed_at >= :since",
      nativeQuery = true)
  Double averageRetention30d(
      @Param("ownerId") UUID ownerId, @Param("deckId") UUID deckId, @Param("since") Instant since);

  /** Counts reviews for an owner across ALL decks in [dayStart, dayEnd). */
  @Query(
      value =
          "SELECT COUNT(*) FROM review_log "
              + "WHERE owner_id = :ownerId "
              + "AND reviewed_at >= :dayStart "
              + "AND reviewed_at < :dayEnd",
      nativeQuery = true)
  long countReviewedTodayGlobal(
      @Param("ownerId") UUID ownerId,
      @Param("dayStart") Instant dayStart,
      @Param("dayEnd") Instant dayEnd);

  /**
   * Returns distinct calendar dates (in given IANA timezone) ordered descending. Uses PostgreSQL AT
   * TIME ZONE. The {@code ::date} projection maps directly to {@link java.time.LocalDate} under
   * Hibernate, so the adapter consumes the list as-is.
   */
  @Query(
      value =
          "SELECT DISTINCT (reviewed_at AT TIME ZONE :tz)::date AS review_date "
              + "FROM review_log "
              + "WHERE owner_id = :ownerId "
              + "ORDER BY review_date DESC",
      nativeQuery = true)
  java.util.List<java.time.LocalDate> distinctReviewDays(
      @Param("ownerId") UUID ownerId, @Param("tz") String tz);

  /** Computes average retention (non-AGAIN fraction) over [since, now) across ALL decks. */
  @Query(
      value =
          "SELECT CAST(SUM(CASE WHEN rating != 'AGAIN' THEN 1 ELSE 0 END) AS DOUBLE PRECISION) "
              + "/ NULLIF(COUNT(*), 0) "
              + "FROM review_log "
              + "WHERE owner_id = :ownerId AND reviewed_at >= :since",
      nativeQuery = true)
  Double averageRetentionGlobal(@Param("ownerId") UUID ownerId, @Param("since") Instant since);
}
