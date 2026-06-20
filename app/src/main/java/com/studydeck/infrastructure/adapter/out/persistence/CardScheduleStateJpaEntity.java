package com.studydeck.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code card_schedule_state} table.
 *
 * <p>1:1 with {@code card}. The card_id is the PK (no surrogate key needed).
 */
@Entity
@Table(name = "card_schedule_state")
class CardScheduleStateJpaEntity {

  @Id
  @Column(name = "card_id", nullable = false)
  private UUID cardId;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(name = "deck_id", nullable = false)
  private UUID deckId;

  @Column(name = "algorithm", nullable = false)
  private String algorithm;

  @Column(name = "state", nullable = false)
  private String state;

  @Column(name = "stability", nullable = false)
  private double stability;

  @Column(name = "difficulty", nullable = false)
  private double difficulty;

  @Column(name = "desired_retention", nullable = false)
  private double desiredRetention;

  @Column(name = "reps", nullable = false)
  private int reps;

  @Column(name = "lapses", nullable = false)
  private int lapses;

  @Column(name = "scheduled_days", nullable = false)
  private int scheduledDays;

  @Column(name = "due_at", nullable = false)
  private Instant dueAt;

  @Column(name = "last_reviewed_at")
  private Instant lastReviewedAt;

  protected CardScheduleStateJpaEntity() {}

  UUID getCardId() {
    return cardId;
  }

  void setCardId(UUID cardId) {
    this.cardId = cardId;
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

  String getAlgorithm() {
    return algorithm;
  }

  void setAlgorithm(String algorithm) {
    this.algorithm = algorithm;
  }

  String getState() {
    return state;
  }

  void setState(String state) {
    this.state = state;
  }

  double getStability() {
    return stability;
  }

  void setStability(double stability) {
    this.stability = stability;
  }

  double getDifficulty() {
    return difficulty;
  }

  void setDifficulty(double difficulty) {
    this.difficulty = difficulty;
  }

  double getDesiredRetention() {
    return desiredRetention;
  }

  void setDesiredRetention(double desiredRetention) {
    this.desiredRetention = desiredRetention;
  }

  int getReps() {
    return reps;
  }

  void setReps(int reps) {
    this.reps = reps;
  }

  int getLapses() {
    return lapses;
  }

  void setLapses(int lapses) {
    this.lapses = lapses;
  }

  int getScheduledDays() {
    return scheduledDays;
  }

  void setScheduledDays(int scheduledDays) {
    this.scheduledDays = scheduledDays;
  }

  Instant getDueAt() {
    return dueAt;
  }

  void setDueAt(Instant dueAt) {
    this.dueAt = dueAt;
  }

  Instant getLastReviewedAt() {
    return lastReviewedAt;
  }

  void setLastReviewedAt(Instant lastReviewedAt) {
    this.lastReviewedAt = lastReviewedAt;
  }
}
