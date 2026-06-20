package com.studydeck.domain.model;

import com.studydeck.domain.exception.DomainValidationException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Note aggregate root.
 *
 * <p>A Note belongs to a {@link Deck} and carries typed content ({@link NoteContent}). The note
 * type is derived from the content variant — there is no separate setter for it.
 *
 * <p>Invariants:
 *
 * <ul>
 *   <li>id, deckId, content: non-null
 *   <li>tags: immutable list; null input → empty list
 *   <li>version: starts at 1, increments on each content update
 * </ul>
 *
 * <p>Pure Java — no Spring, no JPA annotations.
 */
public final class Note {

  private final NoteId id;
  private final DeckId deckId;
  private NoteContent content;
  private List<String> tags;
  private int version;
  private final Instant createdAt;
  private Instant updatedAt;

  private Note(
      NoteId id,
      DeckId deckId,
      NoteContent content,
      List<String> tags,
      int version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.deckId = deckId;
    this.content = content;
    this.tags = tags;
    this.version = version;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * Factory — enforces all creation invariants.
   *
   * @param id non-null
   * @param deckId non-null
   * @param content typed note content (non-null)
   * @param tags nullable; will default to empty list
   */
  public static Note create(NoteId id, DeckId deckId, NoteContent content, List<String> tags) {
    Objects.requireNonNull(id, "Note id must not be null");
    Objects.requireNonNull(deckId, "Note deckId must not be null");
    Objects.requireNonNull(content, "Note content must not be null");
    List<String> safeTags = (tags == null) ? List.of() : List.copyOf(tags);
    Instant now = Instant.now();
    return new Note(id, deckId, content, safeTags, 1, now, now);
  }

  /** Reconstitution constructor for persistence adapters. */
  public static Note reconstitute(
      NoteId id,
      DeckId deckId,
      NoteContent content,
      List<String> tags,
      int version,
      Instant createdAt,
      Instant updatedAt) {
    Objects.requireNonNull(id, "Note id must not be null");
    Objects.requireNonNull(deckId, "Note deckId must not be null");
    Objects.requireNonNull(content, "Note content must not be null");
    return new Note(
        id,
        deckId,
        content,
        (tags == null) ? List.of() : List.copyOf(tags),
        version,
        createdAt,
        updatedAt);
  }

  // ---------------------------------------------------------------
  // State transitions
  // ---------------------------------------------------------------

  /**
   * Updates the content of this note. Increments version and updates updatedAt.
   *
   * @param newContent non-null
   */
  public void updateContent(NoteContent newContent) {
    if (newContent == null) {
      throw new DomainValidationException("content", "must not be null");
    }
    this.content = newContent;
    this.version++;
    this.updatedAt = Instant.now();
  }

  /**
   * Replaces the tag list.
   *
   * @param newTags nullable; null results in empty list
   */
  public void updateTags(List<String> newTags) {
    this.tags = (newTags == null) ? List.of() : List.copyOf(newTags);
    this.updatedAt = Instant.now();
  }

  // ---------------------------------------------------------------
  // Accessors
  // ---------------------------------------------------------------

  public NoteId getId() {
    return id;
  }

  public DeckId getDeckId() {
    return deckId;
  }

  public NoteContent getContent() {
    return content;
  }

  /**
   * Derived from the content type — no separate field. This keeps content and type in sync without
   * any redundant state.
   */
  public NoteType getNoteType() {
    return content.noteType();
  }

  public List<String> getTags() {
    return Collections.unmodifiableList(tags);
  }

  public int getVersion() {
    return version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
