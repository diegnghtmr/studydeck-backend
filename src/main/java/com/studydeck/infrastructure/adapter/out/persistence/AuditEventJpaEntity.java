package com.studydeck.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA entity for the {@code audit_event} table. Immutable after creation. */
@Entity
@Table(name = "audit_event")
class AuditEventJpaEntity {

  @Id private UUID id;

  @Column(name = "actor_id", nullable = false)
  private UUID actorId;

  @Column(nullable = false)
  private String action;

  @Column(name = "target_type", nullable = false)
  private String targetType;

  @Column(name = "target_id", nullable = false)
  private String targetId;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  protected AuditEventJpaEntity() {}

  // --- Getters and setters ---

  UUID getId() {
    return id;
  }

  void setId(UUID id) {
    this.id = id;
  }

  UUID getActorId() {
    return actorId;
  }

  void setActorId(UUID actorId) {
    this.actorId = actorId;
  }

  String getAction() {
    return action;
  }

  void setAction(String action) {
    this.action = action;
  }

  String getTargetType() {
    return targetType;
  }

  void setTargetType(String targetType) {
    this.targetType = targetType;
  }

  String getTargetId() {
    return targetId;
  }

  void setTargetId(String targetId) {
    this.targetId = targetId;
  }

  Instant getOccurredAt() {
    return occurredAt;
  }

  void setOccurredAt(Instant occurredAt) {
    this.occurredAt = occurredAt;
  }
}
