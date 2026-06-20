package com.studydeck.domain.model;

import java.util.List;

/**
 * Sealed hierarchy of typed card payloads — NO raw JSON in the domain.
 *
 * <p>Each note type produces a specific Prompt/Answer pair:
 *
 * <ul>
 *   <li>BASIC → {@link BasicPrompt} / {@link BasicAnswer}
 *   <li>REVERSED → {@link BasicPrompt} / {@link BasicAnswer} (roles swapped for the reverse card)
 *   <li>CLOZE → {@link ClozePrompt} / {@link ClozeAnswer}
 *   <li>MULTIPLE_CHOICE → {@link McqPrompt} / {@link McqAnswer}
 *   <li>FREE_TEXT → {@link FreeTextPrompt} / {@link FreeTextAnswer}
 * </ul>
 *
 * <p>Using a sealed interface keeps the payload hierarchy closed and enables exhaustive pattern
 * matching in infrastructure mappers.
 */
public sealed interface CardPayload
    permits CardPayload.BasicPrompt,
        CardPayload.BasicAnswer,
        CardPayload.ClozePrompt,
        CardPayload.ClozeAnswer,
        CardPayload.McqPrompt,
        CardPayload.McqAnswer,
        CardPayload.FreeTextPrompt,
        CardPayload.FreeTextAnswer {

  // ---------------------------------------------------------------
  // BASIC / REVERSED
  // ---------------------------------------------------------------

  /** Prompt side of a basic or reversed card: shows the front text. */
  record BasicPrompt(String front) implements CardPayload {}

  /** Answer side of a basic or reversed card: reveals the back text. */
  record BasicAnswer(String back) implements CardPayload {}

  // ---------------------------------------------------------------
  // CLOZE
  // ---------------------------------------------------------------

  /**
   * Prompt side of a cloze card.
   *
   * @param deletionNumber the cloze number this card targets (e.g. 1 for c1)
   * @param maskedText full text with the target deletion(s) replaced by {@code [...]} and all other
   *     deletions revealed as plain text
   */
  record ClozePrompt(int deletionNumber, String maskedText) implements CardPayload {}

  /**
   * Answer side of a cloze card.
   *
   * @param fullText original cloze text (with all markers intact)
   * @param deletedText the hidden text(s) for this deletion number (joined by "; " when multiple)
   */
  record ClozeAnswer(String fullText, String deletedText) implements CardPayload {}

  // ---------------------------------------------------------------
  // MULTIPLE CHOICE
  // ---------------------------------------------------------------

  /**
   * Prompt side of an MCQ card: question + visible options.
   *
   * @param question the question text
   * @param options all answer options
   */
  record McqPrompt(String question, List<NoteContent.MultipleChoice.Option> options)
      implements CardPayload {}

  /**
   * Answer side of an MCQ card.
   *
   * @param correctOptionKeys keys of the correct option(s)
   * @param explanation optional explanation; may be null
   */
  record McqAnswer(List<String> correctOptionKeys, String explanation) implements CardPayload {}

  // ---------------------------------------------------------------
  // FREE TEXT
  // ---------------------------------------------------------------

  /** Prompt side of a free-text card: shows the open-ended prompt. */
  record FreeTextPrompt(String prompt) implements CardPayload {}

  /**
   * Answer side of a free-text card.
   *
   * @param expectedAnswer the reference answer
   * @param gradingGuidance optional rubric; may be null
   */
  record FreeTextAnswer(String expectedAnswer, String gradingGuidance) implements CardPayload {}
}
