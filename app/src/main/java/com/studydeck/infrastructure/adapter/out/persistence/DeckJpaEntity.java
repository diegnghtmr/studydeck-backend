package com.studydeck.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code deck} table.
 *
 * <p>Domain model is kept pure; this entity lives exclusively in the infrastructure layer.
 */
@Entity
@Table(name = "deck")
class DeckJpaEntity {

  @Id private UUID id;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(nullable = false)
  private String title;

  @Column private String description;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "tags", columnDefinition = "text[]")
  private List<String> tags = new ArrayList<>();

  @Column(name = "default_desired_retention", nullable = false)
  private double defaultDesiredRetention;

  @Column(nullable = false)
  private boolean archived;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected DeckJpaEntity() {}

  // --- Getters and setters (JPA only — domain model uses Deck) ---

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

  String getTitle() {
    return title;
  }

  void setTitle(String title) {
    this.title = title;
  }

  String getDescription() {
    return description;
  }

  void setDescription(String description) {
    this.description = description;
  }

  List<String> getTags() {
    return tags;
  }

  void setTags(List<String> tags) {
    this.tags = (tags != null) ? new ArrayList<>(tags) : new ArrayList<>();
  }

  double getDefaultDesiredRetention() {
    return defaultDesiredRetention;
  }

  void setDefaultDesiredRetention(double defaultDesiredRetention) {
    this.defaultDesiredRetention = defaultDesiredRetention;
  }

  boolean isArchived() {
    return archived;
  }

  void setArchived(boolean archived) {
    this.archived = archived;
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
