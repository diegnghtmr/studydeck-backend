package com.studydeck.domain.model;

import com.studydeck.domain.exception.DomainValidationException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Deck aggregate root.
 *
 * <p>Invariants:
 *
 * <ul>
 *   <li>title: non-blank, 1–120 characters (OpenAPI contract)
 *   <li>description: optional, max 1000 characters
 *   <li>tags: immutable list; null input → empty list
 *   <li>defaultDesiredRetention: 0.70–0.99 inclusive, default 0.9
 *   <li>archived: defaults to false; archive() transitions to true
 * </ul>
 *
 * <p>Pure Java — no Spring, no JPA annotations.
 */
public final class Deck {

  private static final double DEFAULT_RETENTION = 0.9;
  private static final double MIN_RETENTION = 0.70;
  private static final double MAX_RETENTION = 0.99;

  private static final int MAX_ICON_LENGTH = 40;

  private final DeckId id;
  private final OwnerId ownerId;
  private String title;
  private String description;
  private List<String> tags;
  private double defaultDesiredRetention;
  private boolean archived;
  // Optional user-chosen appearance. Null → the UI derives a stable icon/color from the deck id.
  private String icon;
  private String color;
  private final Instant createdAt;
  private Instant updatedAt;

  private Deck(
      DeckId id,
      OwnerId ownerId,
      String title,
      String description,
      List<String> tags,
      double defaultDesiredRetention,
      boolean archived,
      String icon,
      String color,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.ownerId = ownerId;
    this.title = title;
    this.description = description;
    this.tags = tags;
    this.defaultDesiredRetention = defaultDesiredRetention;
    this.archived = archived;
    this.icon = icon;
    this.color = color;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * Factory — enforces all creation invariants.
   *
   * @param id non-null deck id
   * @param ownerId non-null owner id
   * @param title non-blank, max 120 chars
   * @param description optional, may be null, max 1000 chars
   * @param tags optional, may be null (defaults to empty list)
   * @param defaultDesiredRetention between 0.70 and 0.99 inclusive; use {@code 0.9} as default
   */
  public static Deck create(
      DeckId id,
      OwnerId ownerId,
      String title,
      String description,
      List<String> tags,
      double defaultDesiredRetention) {
    Objects.requireNonNull(id, "Deck id must not be null");
    Objects.requireNonNull(ownerId, "Deck ownerId must not be null");
    validateTitle(title);
    validateDescription(description);
    validateRetention(defaultDesiredRetention);
    List<String> safeTags = (tags == null) ? List.of() : List.copyOf(tags);
    Instant now = Instant.now();
    return new Deck(
        id,
        ownerId,
        title,
        description,
        safeTags,
        defaultDesiredRetention,
        false,
        null,
        null,
        now,
        now);
  }

  /**
   * Convenience factory using default retention (0.9) and no tags.
   *
   * @param id non-null deck id
   * @param ownerId non-null owner id
   * @param title non-blank, max 120 chars
   * @param description optional, may be null
   */
  public static Deck create(DeckId id, OwnerId ownerId, String title, String description) {
    return create(id, ownerId, title, description, null, DEFAULT_RETENTION);
  }

  /** Reconstitution constructor for persistence adapters (no stored appearance). */
  public static Deck reconstitute(
      DeckId id,
      OwnerId ownerId,
      String title,
      String description,
      List<String> tags,
      double defaultDesiredRetention,
      boolean archived,
      Instant createdAt,
      Instant updatedAt) {
    return reconstitute(
        id,
        ownerId,
        title,
        description,
        tags,
        defaultDesiredRetention,
        archived,
        null,
        null,
        createdAt,
        updatedAt);
  }

  /** Reconstitution constructor for persistence adapters, including stored appearance. */
  public static Deck reconstitute(
      DeckId id,
      OwnerId ownerId,
      String title,
      String description,
      List<String> tags,
      double defaultDesiredRetention,
      boolean archived,
      String icon,
      String color,
      Instant createdAt,
      Instant updatedAt) {
    Objects.requireNonNull(id, "Deck id must not be null");
    Objects.requireNonNull(ownerId, "Deck ownerId must not be null");
    validateTitle(title);
    validateDescription(description);
    validateRetention(defaultDesiredRetention);
    validateIcon(icon);
    validateColor(color);
    Objects.requireNonNull(createdAt, "Deck createdAt must not be null");
    Objects.requireNonNull(updatedAt, "Deck updatedAt must not be null");
    List<String> safeTags = (tags == null) ? List.of() : List.copyOf(tags);
    return new Deck(
        id,
        ownerId,
        title,
        description,
        safeTags,
        defaultDesiredRetention,
        archived,
        icon,
        color,
        createdAt,
        updatedAt);
  }

  // ---------------------------------------------------------------
  // State transitions
  // ---------------------------------------------------------------

  /** Archives this deck. Idempotent — calling more than once has no additional effect. */
  public void archive() {
    this.archived = true;
    this.updatedAt = Instant.now();
  }

  /**
   * Updates the deck's mutable fields: title, description, tags, and retention.
   *
   * @param newTitle non-blank, max 120 chars
   * @param newDescription optional, may be null
   * @param newTags optional, null → empty list
   * @param newRetention 0.70–0.99 inclusive
   */
  public void update(
      String newTitle, String newDescription, List<String> newTags, double newRetention) {
    validateTitle(newTitle);
    validateDescription(newDescription);
    validateRetention(newRetention);
    this.title = newTitle;
    this.description = newDescription;
    this.tags = (newTags == null) ? List.of() : List.copyOf(newTags);
    this.defaultDesiredRetention = newRetention;
    this.updatedAt = Instant.now();
  }

  /** Updates title only. Enforces the same invariants as creation. */
  public void retitle(String newTitle) {
    validateTitle(newTitle);
    this.title = newTitle;
    this.updatedAt = Instant.now();
  }

  /** Updates description. Null is allowed. */
  public void updateDescription(String description) {
    validateDescription(description);
    this.description = description;
    this.updatedAt = Instant.now();
  }

  /**
   * Sets the user-chosen appearance (icon glyph name and accent color). Either may be null to clear
   * it and fall back to the id-derived default. Validated against loose format rules; the UI
   * constrains the actual choices.
   */
  public void customizeAppearance(String icon, String color) {
    validateIcon(icon);
    validateColor(color);
    this.icon = icon;
    this.color = color;
    this.updatedAt = Instant.now();
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

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public List<String> getTags() {
    return Collections.unmodifiableList(tags);
  }

  public double getDefaultDesiredRetention() {
    return defaultDesiredRetention;
  }

  public boolean isArchived() {
    return archived;
  }

  /** User-chosen icon glyph name, or null when the id-derived default should be used. */
  public String getIcon() {
    return icon;
  }

  /** User-chosen accent color (hex), or null when the id-derived default should be used. */
  public String getColor() {
    return color;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  // ---------------------------------------------------------------
  // Invariant helpers
  // ---------------------------------------------------------------

  private static void validateTitle(String title) {
    if (title == null || title.isBlank()) {
      throw new DomainValidationException("title", "must not be null or blank");
    }
    if (title.length() > 120) {
      throw new DomainValidationException(
          "title", "exceeds 120 character limit (got %d)".formatted(title.length()));
    }
  }

  private static void validateDescription(String description) {
    if (description != null && description.length() > 1000) {
      throw new DomainValidationException(
          "description", "exceeds 1000 character limit (got %d)".formatted(description.length()));
    }
  }

  private static void validateRetention(double retention) {
    if (retention < MIN_RETENTION || retention > MAX_RETENTION) {
      throw new DomainValidationException(
          "defaultDesiredRetention",
          "must be between 0.70 and 0.99 inclusive (got %s)".formatted(retention));
    }
  }

  private static void validateIcon(String icon) {
    if (icon == null) {
      return;
    }
    if (icon.isBlank()) {
      throw new DomainValidationException("icon", "must not be blank when provided");
    }
    if (icon.length() > MAX_ICON_LENGTH) {
      throw new DomainValidationException(
          "icon", "exceeds %d character limit (got %d)".formatted(MAX_ICON_LENGTH, icon.length()));
    }
  }

  private static void validateColor(String color) {
    if (color == null) {
      return;
    }
    if (!color.matches("^#[0-9a-fA-F]{6}$")) {
      throw new DomainValidationException(
          "color", "must be a 6-digit hex color like #ff3e00 (got %s)".formatted(color));
    }
  }
}
