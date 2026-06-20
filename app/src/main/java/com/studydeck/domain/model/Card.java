package com.studydeck.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Card entity.
 *
 * <p>A card is derived from a {@link Note}. It holds typed {@link CardPayload} instances for its
 * prompt and answer sides — there is no raw JSON in the domain.
 *
 * <p>Invariants:
 *
 * <ul>
 *   <li>id, noteId, noteType, promptPayload, answerPayload: non-null
 *   <li>ordinal: 0-based position within cards derived from the same note
 *   <li>suspended: defaults to false
 * </ul>
 *
 * <p>Pure Java — no Spring, no JPA annotations.
 */
public final class Card {

  private final CardId id;
  private final NoteId noteId;
  private final NoteType noteType;
  private final String cardVariant;
  private final int ordinal;
  private final CardPayload promptPayload;
  private final CardPayload answerPayload;
  private boolean suspended;
  private final Instant createdAt;

  private Card(
      CardId id,
      NoteId noteId,
      NoteType noteType,
      String cardVariant,
      int ordinal,
      CardPayload promptPayload,
      CardPayload answerPayload,
      boolean suspended,
      Instant createdAt) {
    this.id = id;
    this.noteId = noteId;
    this.noteType = noteType;
    this.cardVariant = cardVariant;
    this.ordinal = ordinal;
    this.promptPayload = promptPayload;
    this.answerPayload = answerPayload;
    this.suspended = suspended;
    this.createdAt = createdAt;
  }

  /**
   * Factory used by {@link com.studydeck.domain.service.CardGenerator}.
   *
   * @param id unique card id
   * @param noteId the note this card derives from
   * @param noteType the note type (copied from the Note)
   * @param cardVariant variant name: "forward", "reverse", "cloze-N", "mcq", "free-text"
   * @param ordinal 0-based position within sibling cards
   * @param promptPayload typed prompt (non-null)
   * @param answerPayload typed answer (non-null)
   */
  public static Card create(
      CardId id,
      NoteId noteId,
      NoteType noteType,
      String cardVariant,
      int ordinal,
      CardPayload promptPayload,
      CardPayload answerPayload) {
    Objects.requireNonNull(id, "Card id must not be null");
    Objects.requireNonNull(noteId, "Card noteId must not be null");
    Objects.requireNonNull(noteType, "Card noteType must not be null");
    Objects.requireNonNull(cardVariant, "Card cardVariant must not be null");
    Objects.requireNonNull(promptPayload, "Card promptPayload must not be null");
    Objects.requireNonNull(answerPayload, "Card answerPayload must not be null");
    return new Card(
        id,
        noteId,
        noteType,
        cardVariant,
        ordinal,
        promptPayload,
        answerPayload,
        false,
        Instant.now());
  }

  /**
   * Reconstitution constructor for persistence adapters.
   *
   * @param id unique card id
   * @param noteId the note this card derives from
   * @param noteType the note type (copied from the Note)
   * @param cardVariant variant name: "forward", "reverse", "cloze-N", "mcq", "free-text"
   * @param ordinal 0-based position within sibling cards
   * @param promptPayload typed prompt (non-null)
   * @param answerPayload typed answer (non-null)
   * @param suspended whether the card is suspended
   * @param createdAt original creation timestamp
   */
  public static Card reconstitute(
      CardId id,
      NoteId noteId,
      NoteType noteType,
      String cardVariant,
      int ordinal,
      CardPayload promptPayload,
      CardPayload answerPayload,
      boolean suspended,
      Instant createdAt) {
    Objects.requireNonNull(id, "Card id must not be null");
    Objects.requireNonNull(noteId, "Card noteId must not be null");
    Objects.requireNonNull(noteType, "Card noteType must not be null");
    Objects.requireNonNull(cardVariant, "Card cardVariant must not be null");
    Objects.requireNonNull(promptPayload, "Card promptPayload must not be null");
    Objects.requireNonNull(answerPayload, "Card answerPayload must not be null");
    Objects.requireNonNull(createdAt, "Card createdAt must not be null");
    return new Card(
        id,
        noteId,
        noteType,
        cardVariant,
        ordinal,
        promptPayload,
        answerPayload,
        suspended,
        createdAt);
  }

  // ---------------------------------------------------------------
  // State transitions
  // ---------------------------------------------------------------

  public void suspend() {
    this.suspended = true;
  }

  public void unsuspend() {
    this.suspended = false;
  }

  // ---------------------------------------------------------------
  // Accessors
  // ---------------------------------------------------------------

  public CardId getId() {
    return id;
  }

  public NoteId getNoteId() {
    return noteId;
  }

  public NoteType getNoteType() {
    return noteType;
  }

  public String getCardVariant() {
    return cardVariant;
  }

  public int getOrdinal() {
    return ordinal;
  }

  public CardPayload getPromptPayload() {
    return promptPayload;
  }

  public CardPayload getAnswerPayload() {
    return answerPayload;
  }

  public boolean isSuspended() {
    return suspended;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
