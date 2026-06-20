package com.studydeck.domain.service;

import com.studydeck.domain.model.Card;
import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.CardPayload;
import com.studydeck.domain.model.Note;
import com.studydeck.domain.model.NoteContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;

/**
 * Domain service that derives {@link Card}s from a {@link Note}.
 *
 * <p>Card generation rules (P0):
 *
 * <ul>
 *   <li>BASIC → 1 card (forward: front→back)
 *   <li>REVERSED → 2 cards (ordinal 0: forward, ordinal 1: reverse back→front)
 *   <li>CLOZE → 1 card per distinct cloze number; each card masks only its own deletions
 *   <li>MULTIPLE_CHOICE → 1 card (question + all options as prompt, correct key(s) as answer)
 *   <li>FREE_TEXT → 1 card (prompt → expected answer + guidance)
 * </ul>
 *
 * <p>Pure Java — no Spring, no JPA. Stateless; safe to share.
 */
public final class CardGenerator {

  /**
   * Generates all cards for the given note.
   *
   * @param note the source note (non-null)
   * @return immutable list of cards, never empty
   * @throws NullPointerException if note is null
   */
  public List<Card> generate(Note note) {
    Objects.requireNonNull(note, "Note must not be null");

    return switch (note.getContent()) {
      case NoteContent.Basic b -> generateBasic(note, b);
      case NoteContent.Reversed r -> generateReversed(note, r);
      case NoteContent.Cloze c -> generateCloze(note, c);
      case NoteContent.MultipleChoice m -> generateMultipleChoice(note, m);
      case NoteContent.FreeText f -> generateFreeText(note, f);
    };
  }

  // ---------------------------------------------------------------
  // BASIC
  // ---------------------------------------------------------------

  private List<Card> generateBasic(Note note, NoteContent.Basic content) {
    Card card =
        Card.create(
            CardId.generate(),
            note.getId(),
            note.getNoteType(),
            "forward",
            0,
            new CardPayload.BasicPrompt(content.front()),
            new CardPayload.BasicAnswer(content.back()));
    return List.of(card);
  }

  // ---------------------------------------------------------------
  // REVERSED
  // ---------------------------------------------------------------

  private List<Card> generateReversed(Note note, NoteContent.Reversed content) {
    Card forward =
        Card.create(
            CardId.generate(),
            note.getId(),
            note.getNoteType(),
            "forward",
            0,
            new CardPayload.BasicPrompt(content.front()),
            new CardPayload.BasicAnswer(content.back()));

    Card reverse =
        Card.create(
            CardId.generate(),
            note.getId(),
            note.getNoteType(),
            "reverse",
            1,
            // Reverse: back is the new "front" prompt; answer reveals the original front
            new CardPayload.BasicPrompt(content.back()),
            new CardPayload.BasicAnswer(content.front()));

    return List.of(forward, reverse);
  }

  // ---------------------------------------------------------------
  // CLOZE
  // ---------------------------------------------------------------

  private List<Card> generateCloze(Note note, NoteContent.Cloze content) {
    SortedSet<Integer> deletionNumbers = content.parseDeletionNumbers();
    List<Card> cards = new ArrayList<>(deletionNumbers.size());

    int ordinal = 0;
    for (int n : deletionNumbers) {
      String maskedText = content.maskedFor(n);
      String deletedText = content.deletedTextFor(n);

      Card card =
          Card.create(
              CardId.generate(),
              note.getId(),
              note.getNoteType(),
              "cloze-" + n,
              ordinal,
              new CardPayload.ClozePrompt(n, maskedText),
              new CardPayload.ClozeAnswer(content.text(), deletedText));
      cards.add(card);
      ordinal++;
    }

    return List.copyOf(cards);
  }

  // ---------------------------------------------------------------
  // MULTIPLE CHOICE
  // ---------------------------------------------------------------

  private List<Card> generateMultipleChoice(Note note, NoteContent.MultipleChoice content) {
    Card card =
        Card.create(
            CardId.generate(),
            note.getId(),
            note.getNoteType(),
            "mcq",
            0,
            new CardPayload.McqPrompt(content.question(), List.copyOf(content.options())),
            new CardPayload.McqAnswer(
                List.copyOf(content.correctOptionKeys()), content.explanation()));
    return List.of(card);
  }

  // ---------------------------------------------------------------
  // FREE TEXT
  // ---------------------------------------------------------------

  private List<Card> generateFreeText(Note note, NoteContent.FreeText content) {
    Card card =
        Card.create(
            CardId.generate(),
            note.getId(),
            note.getNoteType(),
            "free-text",
            0,
            new CardPayload.FreeTextPrompt(content.prompt()),
            new CardPayload.FreeTextAnswer(content.expectedAnswer(), content.gradingGuidance()));
    return List.of(card);
  }
}
