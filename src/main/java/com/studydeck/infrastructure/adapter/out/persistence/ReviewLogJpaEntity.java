package com.studydeck.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code review_log} table.
 *
 * <p>Immutable once persisted — append-only log of review events.
 */
@Entity
@Table(name = "review_log")
class ReviewLogJpaEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(name = "session_id")
  private UUID sessionId;

  @Column(name = "card_id", nullable = false)
  private UUID cardId;

  @Column(name = "deck_id", nullable = false)
  private UUID deckId;

  @Column(name = "rating", nullable = false)
  private String rating;

  @Column(name = "state_before", nullable = false)
  private String stateBefore;

  @Column(name = "elapsed_days", nullable = false)
  private int elapsedDays;

  @Column(name = "scheduled_days", nullable = false)
  private int scheduledDays;

  @Column(name = "response_time_ms")
  private Integer responseTimeMs;

  @Column(name = "reviewed_at", nullable = false)
  private Instant reviewedAt;

  protected ReviewLogJpaEntity() {}

  UUID getId() {
    return id;
  }

  void setId(UUID id) {
    this.id = id;
  }

  UUID getOwnerId() {
    return ownerId;
  }

  void setOwnerId(UUID ownerId) {
    this.ownerId = ownerId;
  }

  UUID getSessionId() {
    return sessionId;
  }

  void setSessionId(UUID sessionId) {
    this.sessionId = sessionId;
  }

  UUID getCardId() {
    return cardId;
  }

  void setCardId(UUID cardId) {
    this.cardId = cardId;
  }

  UUID getDeckId() {
    return deckId;
  }

  void setDeckId(UUID deckId) {
    this.deckId = deckId;
  }

  String getRating() {
    return rating;
  }

  void setRating(String rating) {
    this.rating = rating;
  }

  String getStateBefore() {
    return stateBefore;
  }

  void setStateBefore(String stateBefore) {
    this.stateBefore = stateBefore;
  }

  int getElapsedDays() {
    return elapsedDays;
  }

  void setElapsedDays(int elapsedDays) {
    this.elapsedDays = elapsedDays;
  }

  int getScheduledDays() {
    return scheduledDays;
  }

  void setScheduledDays(int scheduledDays) {
    this.scheduledDays = scheduledDays;
  }

  Integer getResponseTimeMs() {
    return responseTimeMs;
  }

  void setResponseTimeMs(Integer responseTimeMs) {
    this.responseTimeMs = responseTimeMs;
  }

  Instant getReviewedAt() {
    return reviewedAt;
  }

  void setReviewedAt(Instant reviewedAt) {
    this.reviewedAt = reviewedAt;
  }
}
