package com.studydeck.domain.model;

import com.studydeck.domain.exception.DomainValidationException;
import java.time.Instant;
import java.util.Objects;

/**
 * Deck aggregate root.
 *
 * <p>Invariants:
 *
 * <ul>
 *   <li>name: non-blank, 1–200 characters
 *   <li>visibility: defaults to PRIVATE
 *   <li>archived: defaults to false; archive() transitions to true
 * </ul>
 *
 * <p>Pure Java — no Spring, no JPA annotations.
 */
public final class Deck {

  private final DeckId id;
  private final OwnerId ownerId;
  private String name;
  private String description;
  private Visibility visibility;
  private boolean archived;
  private final Instant createdAt;

  private Deck(
      DeckId id,
      OwnerId ownerId,
      String name,
      String description,
      Visibility visibility,
      boolean archived,
      Instant createdAt) {
    this.id = id;
    this.ownerId = ownerId;
    this.name = name;
    this.description = description;
    this.visibility = visibility;
    this.archived = archived;
    this.createdAt = createdAt;
  }

  /**
   * Factory — enforces all creation invariants.
   *
   * @param id non-null deck id
   * @param ownerId non-null owner id
   * @param name non-blank, max 200 chars
   * @param description optional, may be null
   */
  public static Deck create(DeckId id, OwnerId ownerId, String name, String description) {
    Objects.requireNonNull(id, "Deck id must not be null");
    Objects.requireNonNull(ownerId, "Deck ownerId must not be null");
    validateName(name);
    return new Deck(id, ownerId, name, description, Visibility.PRIVATE, false, Instant.now());
  }

  /** Reconstitution constructor for persistence adapters (package-private by convention). */
  public static Deck reconstitute(
      DeckId id,
      OwnerId ownerId,
      String name,
      String description,
      Visibility visibility,
      boolean archived,
      Instant createdAt) {
    Objects.requireNonNull(id, "Deck id must not be null");
    Objects.requireNonNull(ownerId, "Deck ownerId must not be null");
    validateName(name);
    Objects.requireNonNull(createdAt, "Deck createdAt must not be null");
    return new Deck(id, ownerId, name, description, visibility, archived, createdAt);
  }

  // ---------------------------------------------------------------
  // State transitions
  // ---------------------------------------------------------------

  /** Archives this deck. Idempotent — calling more than once has no additional effect. */
  public void archive() {
    this.archived = true;
  }

  /** Updates name. Enforces the same invariants as creation. */
  public void rename(String newName) {
    validateName(newName);
    this.name = newName;
  }

  /** Updates description. Null is allowed. */
  public void updateDescription(String description) {
    this.description = description;
  }

  // ---------------------------------------------------------------
  // Accessors
  // ---------------------------------------------------------------

  public DeckId getId() {
    return id;
  }

  public OwnerId getOwnerId() {
    return ownerId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public Visibility getVisibility() {
    return visibility;
  }

  public boolean isArchived() {
    return archived;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  // ---------------------------------------------------------------
  // Invariant helpers
  // ---------------------------------------------------------------

  private static void validateName(String name) {
    if (name == null || name.isBlank()) {
      throw new DomainValidationException("name", "must not be null or blank");
    }
    if (name.length() > 200) {
      throw new DomainValidationException(
          "name", "exceeds 200 character limit (got %d)".formatted(name.length()));
    }
  }
}
