package com.studydeck.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root for a user-owned BYOK AI provider configuration.
 *
 * <p>Holds {@code apiKeyCiphertext} ONLY — the plaintext API key is NEVER stored here. The
 * application service is the sole transient-plaintext boundary.
 *
 * <p>Pure Java — no Spring, no Jakarta imports (ArchUnit enforces domain layer purity).
 */
public class UserAiProvider {

  private final UserAiProviderId id;
  private final OwnerId ownerId;
  private String label;
  private String baseUrl;
  private String model;

  /** AES-256-GCM ciphertext: Base64( IV(12) || GCM(ct+tag) ). Never the plaintext key. */
  private String apiKeyCiphertext;

  /** Non-secret masked display hint stored at write time; list never needs to decrypt. */
  private String keyHint;

  private boolean active;
  private Instant createdAt;
  private Instant updatedAt;

  private UserAiProvider(
      UserAiProviderId id,
      OwnerId ownerId,
      String label,
      String baseUrl,
      String model,
      String apiKeyCiphertext,
      String keyHint,
      boolean active,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
    setLabel(label);
    setBaseUrl(baseUrl);
    setModel(model);
    this.apiKeyCiphertext =
        Objects.requireNonNull(apiKeyCiphertext, "apiKeyCiphertext must not be null");
    this.keyHint = Objects.requireNonNull(keyHint, "keyHint must not be null");
    this.active = active;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
  }

  /** Factory method for creating or reconstituting a {@link UserAiProvider}. */
  public static UserAiProvider create(
      UserAiProviderId id,
      OwnerId ownerId,
      String label,
      String baseUrl,
      String model,
      String apiKeyCiphertext,
      String keyHint,
      boolean active,
      Instant createdAt,
      Instant updatedAt) {
    return new UserAiProvider(
        id,
        ownerId,
        label,
        baseUrl,
        model,
        apiKeyCiphertext,
        keyHint,
        active,
        createdAt,
        updatedAt);
  }

  // -----------------------------------------------------------------------
  // Behaviour
  // -----------------------------------------------------------------------

  public void activate() {
    this.active = true;
  }

  public void deactivate() {
    this.active = false;
  }

  /**
   * Updates the ciphertext and key hint atomically; returns {@code this} for method chaining.
   *
   * @param newCiphertext the new AES-GCM ciphertext
   * @param newKeyHint the new masked hint computed from the new plaintext
   * @return this instance (mutable aggregate)
   */
  public UserAiProvider withCiphertext(String newCiphertext, String newKeyHint) {
    this.apiKeyCiphertext = Objects.requireNonNull(newCiphertext, "newCiphertext must not be null");
    this.keyHint = Objects.requireNonNull(newKeyHint, "newKeyHint must not be null");
    return this;
  }

  public void updateConfig(String label, String baseUrl, String model, Instant updatedAt) {
    setLabel(label);
    setBaseUrl(baseUrl);
    setModel(model);
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
  }

  // -----------------------------------------------------------------------
  // Invariants
  // -----------------------------------------------------------------------

  private void setLabel(String label) {
    if (label == null || label.isBlank()) {
      throw new IllegalArgumentException("UserAiProvider.label must not be blank");
    }
    this.label = label;
  }

  private void setBaseUrl(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("UserAiProvider.baseUrl must not be blank");
    }
    this.baseUrl = baseUrl;
  }

  private void setModel(String model) {
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("UserAiProvider.model must not be blank");
    }
    this.model = model;
  }

  // -----------------------------------------------------------------------
  // Accessors
  // -----------------------------------------------------------------------

  public UserAiProviderId getId() {
    return id;
  }

  public OwnerId getOwnerId() {
    return ownerId;
  }

  public String getLabel() {
    return label;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public String getModel() {
    return model;
  }

  public String getApiKeyCiphertext() {
    return apiKeyCiphertext;
  }

  public String getKeyHint() {
    return keyHint;
  }

  public boolean isActive() {
    return active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
