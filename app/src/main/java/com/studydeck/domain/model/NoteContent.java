package com.studydeck.domain.model;

import com.studydeck.domain.exception.DomainValidationException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sealed hierarchy representing the structured content of each note type.
 *
 * <p>Permitted subtypes (P0):
 *
 * <ul>
 *   <li>{@link Basic} — front + back
 *   <li>{@link Reversed} — same fields as Basic, but also generates a reverse card
 *   <li>{@link Cloze} — text with {@code {{cN::deletion}}} markers
 *   <li>{@link MultipleChoice} — question + 4-5 options + correct key
 *   <li>{@link FreeText} — open prompt + expected answer + optional rubric
 * </ul>
 *
 * <p>All records self-validate in compact constructors — no nulls escape.
 */
public sealed interface NoteContent
    permits NoteContent.Basic,
        NoteContent.Reversed,
        NoteContent.Cloze,
        NoteContent.MultipleChoice,
        NoteContent.FreeText {

  /** Returns the {@link NoteType} that corresponds to this content variant. */
  NoteType noteType();

  // ---------------------------------------------------------------
  // BASIC
  // ---------------------------------------------------------------

  /**
   * Content for a basic flashcard: a single front and back.
   *
   * <p>front: 1–1000 chars. back: 1–5000 chars.
   */
  record Basic(String front, String back) implements NoteContent {

    public Basic {
      requireNonBlank(front, "front", 1000);
      requireNonBlank(back, "back", 5000);
    }

    @Override
    public NoteType noteType() {
      return NoteType.BASIC;
    }
  }

  // ---------------------------------------------------------------
  // REVERSED
  // ---------------------------------------------------------------

  /**
   * Content for a reversed flashcard: generates both a forward and a backward card.
   *
   * <p>Same field constraints as {@link Basic}.
   */
  record Reversed(String front, String back) implements NoteContent {

    public Reversed {
      requireNonBlank(front, "front", 1000);
      requireNonBlank(back, "back", 5000);
    }

    @Override
    public NoteType noteType() {
      return NoteType.REVERSED;
    }
  }

  // ---------------------------------------------------------------
  // CLOZE
  // ---------------------------------------------------------------

  /**
   * Content for a cloze-deletion note.
   *
   * <p>text must contain at least one {@code {{cN::deletion}}} marker (N = positive integer). text
   * max 5000 chars.
   */
  record Cloze(String text) implements NoteContent {

    /** Pattern matching a single cloze deletion: {@code {{cN::...}}}. */
    static final Pattern DELETION_PATTERN = Pattern.compile("\\{\\{c(\\d+)::([^}]+)\\}\\}");

    public Cloze {
      if (text == null || text.isBlank()) {
        throw new DomainValidationException("text", "cloze text must not be blank");
      }
      if (text.length() > 5000) {
        throw new DomainValidationException("text", "cloze text exceeds 5000 characters");
      }
      if (!DELETION_PATTERN.matcher(text).find()) {
        throw new DomainValidationException(
            "cloze", "text must contain at least one {{cN::deletion}} marker");
      }
    }

    @Override
    public NoteType noteType() {
      return NoteType.CLOZE;
    }

    /**
     * Returns the sorted, deduplicated set of deletion numbers present in the text.
     *
     * <p>For example, {@code "{{c2::b}} {{c1::a}} {{c2::also-b}}"} returns {@code [1, 2]}.
     */
    public SortedSet<Integer> parseDeletionNumbers() {
      Matcher m = DELETION_PATTERN.matcher(text);
      SortedSet<Integer> numbers = new TreeSet<>();
      while (m.find()) {
        numbers.add(Integer.parseInt(m.group(1)));
      }
      return Collections.unmodifiableSortedSet(numbers);
    }

    /**
     * Returns the text with all occurrences of the given deletion number replaced by {@code [...]},
     * while all OTHER deletions are replaced by their plain text values (i.e. revealed).
     *
     * <p>Example: maskedFor(1) on {@code "{{c1::Java}} runs on {{c2::JVM}}"} → {@code "[...] runs
     * on JVM"}.
     */
    public String maskedFor(int deletionNumber) {
      Matcher m = DELETION_PATTERN.matcher(text);
      StringBuilder sb = new StringBuilder();
      while (m.find()) {
        int n = Integer.parseInt(m.group(1));
        String replacement = (n == deletionNumber) ? "[...]" : m.group(2);
        m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
      }
      m.appendTail(sb);
      return sb.toString();
    }

    /**
     * Returns the concatenated text of all deletions with the given deletion number, separated by
     * "; " when there are multiple occurrences.
     */
    public String deletedTextFor(int deletionNumber) {
      Matcher m = DELETION_PATTERN.matcher(text);
      StringBuilder sb = new StringBuilder();
      while (m.find()) {
        if (Integer.parseInt(m.group(1)) == deletionNumber) {
          if (!sb.isEmpty()) sb.append("; ");
          sb.append(m.group(2));
        }
      }
      return sb.toString();
    }
  }

  // ---------------------------------------------------------------
  // MULTIPLE CHOICE
  // ---------------------------------------------------------------

  /**
   * Content for a multiple-choice note.
   *
   * <p>Constraints (per OpenAPI spec):
   *
   * <ul>
   *   <li>question: 1–2000 chars
   *   <li>options: exactly 4 or 5 items
   *   <li>correctOptionKeys: exactly 1 key, must reference an existing option key
   * </ul>
   */
  record MultipleChoice(
      String question, List<Option> options, List<String> correctOptionKeys, String explanation)
      implements NoteContent {

    /**
     * A single option in a multiple-choice question.
     *
     * <p>key: single uppercase letter (A–Z). text: 1–1000 chars.
     */
    public record Option(String key, String text) {

      public Option {
        if (key == null || !key.matches("^[A-Z]$")) {
          throw new DomainValidationException(
              "key", "option key must be a single uppercase letter, got: " + key);
        }
        requireNonBlank(text, "text", 1000);
      }
    }

    public MultipleChoice {
      requireNonBlank(question, "question", 2000);
      Objects.requireNonNull(options, "options must not be null");
      if (options.size() < 4 || options.size() > 5) {
        throw new DomainValidationException(
            "options", "must have 4 or 5 options, got: " + options.size());
      }
      Objects.requireNonNull(correctOptionKeys, "correctOptionKeys must not be null");
      if (correctOptionKeys.isEmpty()) {
        throw new DomainValidationException("correct", "at least one correct option key required");
      }
      var optionKeySet =
          options.stream().map(Option::key).collect(java.util.stream.Collectors.toSet());
      for (String ck : correctOptionKeys) {
        if (!optionKeySet.contains(ck)) {
          throw new DomainValidationException(
              "correct", "correct option key '%s' not found in options".formatted(ck));
        }
      }
      options = List.copyOf(options);
      correctOptionKeys = List.copyOf(correctOptionKeys);
    }

    @Override
    public NoteType noteType() {
      return NoteType.MULTIPLE_CHOICE;
    }
  }

  // ---------------------------------------------------------------
  // FREE TEXT
  // ---------------------------------------------------------------

  /**
   * Content for an open-ended free-text note.
   *
   * <p>prompt: 1–2000 chars. expectedAnswer: 1–5000 chars. gradingGuidance: optional, max 2000
   * chars.
   */
  record FreeText(String prompt, String expectedAnswer, String gradingGuidance)
      implements NoteContent {

    public FreeText {
      requireNonBlank(prompt, "prompt", 2000);
      requireNonBlank(expectedAnswer, "expectedAnswer", 5000);
      if (gradingGuidance != null && gradingGuidance.length() > 2000) {
        throw new DomainValidationException("gradingGuidance", "exceeds 2000 character limit");
      }
    }

    @Override
    public NoteType noteType() {
      return NoteType.FREE_TEXT;
    }
  }

  // ---------------------------------------------------------------
  // Shared validation helpers (package-private statics on interface)
  // ---------------------------------------------------------------

  private static void requireNonBlank(String value, String fieldName, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new DomainValidationException(fieldName, "must not be null or blank");
    }
    if (value.length() > maxLength) {
      throw new DomainValidationException(
          fieldName, "exceeds %d character limit (got %d)".formatted(maxLength, value.length()));
    }
  }
}
