package com.studydeck.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA entity for the {@code review_session} table. */
@Entity
@Table(name = "review_session")
class ReviewSessionJpaEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(name = "deck_id")
  private UUID deckId;

  @Column(name = "max_cards", nullable = false)
  private int maxCards;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "presented_count", nullable = false)
  private int presentedCount;

  @Column(name = "answered_count", nullable = false)
  private int answeredCount;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  protected ReviewSessionJpaEntity() {}

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

  UUID getDeckId() {
    return deckId;
  }

  void setDeckId(UUID deckId) {
    this.deckId = deckId;
  }

  int getMaxCards() {
    return maxCards;
  }

  void setMaxCards(int maxCards) {
    this.maxCards = maxCards;
  }

  String getStatus() {
    return status;
  }

  void setStatus(String status) {
    this.status = status;
  }

  int getPresentedCount() {
    return presentedCount;
  }

  void setPresentedCount(int presentedCount) {
    this.presentedCount = presentedCount;
  }

  int getAnsweredCount() {
    return answeredCount;
  }

  void setAnsweredCount(int answeredCount) {
    this.answeredCount = answeredCount;
  }

  Instant getStartedAt() {
    return startedAt;
  }

  void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  Instant getEndedAt() {
    return endedAt;
  }

  void setEndedAt(Instant endedAt) {
    this.endedAt = endedAt;
  }
}
