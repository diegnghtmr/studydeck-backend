package com.studydeck.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code user_ai_provider} table.
 *
 * <p>Domain model is kept pure; this entity lives exclusively in the infrastructure layer.
 * Package-private — only accessible within the persistence package.
 */
@Entity
@Table(name = "user_ai_provider")
class UserAiProviderJpaEntity {

  @Id private UUID id;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(nullable = false)
  private String label;

  @Column(name = "base_url", nullable = false)
  private String baseUrl;

  @Column(nullable = false)
  private String model;

  /** AES-256-GCM ciphertext: Base64( IV(12) || GCM(ct+tag) ). Never returned to clients. */
  @Column(name = "api_key_ciphertext", nullable = false)
  private String apiKeyCiphertext;

  /** Non-secret masked display hint (first4…last4 or •••••). */
  @Column(name = "key_hint", nullable = false)
  private String keyHint;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserAiProviderJpaEntity() {}

  // --- Getters and setters (JPA only — domain model uses UserAiProvider) ---

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

  String getLabel() {
    return label;
  }

  void setLabel(String label) {
    this.label = label;
  }

  String getBaseUrl() {
    return baseUrl;
  }

  void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  String getModel() {
    return model;
  }

  void setModel(String model) {
    this.model = model;
  }

  String getApiKeyCiphertext() {
    return apiKeyCiphertext;
  }

  void setApiKeyCiphertext(String apiKeyCiphertext) {
    this.apiKeyCiphertext = apiKeyCiphertext;
  }

  String getKeyHint() {
    return keyHint;
  }

  void setKeyHint(String keyHint) {
    this.keyHint = keyHint;
  }

  boolean isActive() {
    return active;
  }

  void setActive(boolean active) {
    this.active = active;
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
