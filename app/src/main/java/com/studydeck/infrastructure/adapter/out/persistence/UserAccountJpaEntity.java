package com.studydeck.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code user_account} table.
 *
 * <p>Domain model is kept pure; this entity lives exclusively in the infrastructure layer.
 */
@Entity
@Table(name = "user_account")
class UserAccountJpaEntity {

  @Id private UUID id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(nullable = false)
  private String status;

  @Column(name = "daily_goal", nullable = false)
  private int dailyGoal = 40;

  @Column(name = "desired_retention", nullable = false, precision = 3, scale = 2)
  private BigDecimal desiredRetention = new BigDecimal("0.90");

  @Column(name = "new_cards_per_day", nullable = false)
  private int newCardsPerDay = 10;

  @Column(name = "language", nullable = false, length = 5)
  private String language = "en";

  @Column(name = "timezone", nullable = false, length = 64)
  private String timezone = "UTC";

  @Column(name = "scheduler_algorithm", nullable = false, length = 8)
  private String schedulerAlgorithm = "FSRS";

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserAccountJpaEntity() {}

  // --- Getters and setters (JPA only — domain model uses UserAccount) ---

  UUID getId() {
    return id;
  }

  void setId(UUID id) {
    this.id = id;
  }

  String getEmail() {
    return email;
  }

  void setEmail(String email) {
    this.email = email;
  }

  String getDisplayName() {
    return displayName;
  }

  void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  String getStatus() {
    return status;
  }

  void setStatus(String status) {
    this.status = status;
  }

  int getDailyGoal() {
    return dailyGoal;
  }

  void setDailyGoal(int dailyGoal) {
    this.dailyGoal = dailyGoal;
  }

  BigDecimal getDesiredRetention() {
    return desiredRetention;
  }

  void setDesiredRetention(BigDecimal desiredRetention) {
    this.desiredRetention = desiredRetention;
  }

  int getNewCardsPerDay() {
    return newCardsPerDay;
  }

  void setNewCardsPerDay(int newCardsPerDay) {
    this.newCardsPerDay = newCardsPerDay;
  }

  String getLanguage() {
    return language;
  }

  void setLanguage(String language) {
    this.language = language;
  }

  String getTimezone() {
    return timezone;
  }

  void setTimezone(String timezone) {
    this.timezone = timezone;
  }

  String getSchedulerAlgorithm() {
    return schedulerAlgorithm;
  }

  void setSchedulerAlgorithm(String schedulerAlgorithm) {
    this.schedulerAlgorithm = schedulerAlgorithm;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }

  void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
