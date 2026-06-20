package com.studydeck.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.studydeck.domain.model.NoteContent;
import com.studydeck.domain.port.in.ValidateImportUseCase.ImportPayload.NoteImport;
import com.studydeck.domain.port.in.ValidateImportUseCase.ImportPayload.OptionImport;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NoteContentHasher}.
 *
 * <p>Key invariants:
 *
 * <ul>
 *   <li>Same content → same hash (deterministic)
 *   <li>Different content → different hash
 *   <li>Minor whitespace differences → same hash (normalized)
 *   <li>Different note types with same text → different hash (type-scoped)
 *   <li>NoteContent and NoteImport with same logical content → same hash
 * </ul>
 */
class NoteContentHasherTest {

  // ---------------------------------------------------------------
  // Basic
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("Basic notes")
  class BasicNotes {

    @Test
    @DisplayName("same content produces same hash")
    void sameContentSameHash() {
      NoteContent.Basic a = new NoteContent.Basic("What is Java?", "A programming language.");
      NoteContent.Basic b = new NoteContent.Basic("What is Java?", "A programming language.");

      assertThat(NoteContentHasher.hash(a)).isEqualTo(NoteContentHasher.hash(b));
    }

    @Test
    @DisplayName("different content produces different hash")
    void differentContentDifferentHash() {
      NoteContent.Basic a = new NoteContent.Basic("What is Java?", "A programming language.");
      NoteContent.Basic b = new NoteContent.Basic("What is Python?", "A programming language.");

      assertThat(NoteContentHasher.hash(a)).isNotEqualTo(NoteContentHasher.hash(b));
    }

    @Test
    @DisplayName("leading/trailing whitespace is normalized")
    void whitespaceTrimmed() {
      NoteContent.Basic a =
          new NoteContent.Basic("  What is Java?  ", "  A programming language.  ");
      NoteContent.Basic b = new NoteContent.Basic("What is Java?", "A programming language.");

      assertThat(NoteContentHasher.hash(a)).isEqualTo(NoteContentHasher.hash(b));
    }

    @Test
    @DisplayName("NoteContent.Basic and NoteImport basic produce same hash")
    void domainAndImportHashMatch() {
      NoteContent.Basic domain = new NoteContent.Basic("Front", "Back");
      NoteImport imported =
          new NoteImport(
              "basic", "Front", "Back", null, null, null, null, null, null, null, null, null, null);

      assertThat(NoteContentHasher.hash(domain)).isEqualTo(NoteContentHasher.hash(imported));
    }
  }

  // ---------------------------------------------------------------
  // Reversed
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("Reversed notes")
  class ReversedNotes {

    @Test
    @DisplayName("same type and content = same hash")
    void sameHash() {
      NoteContent.Reversed a = new NoteContent.Reversed("Front", "Back");
      NoteContent.Reversed b = new NoteContent.Reversed("Front", "Back");

      assertThat(NoteContentHasher.hash(a)).isEqualTo(NoteContentHasher.hash(b));
    }

    @Test
    @DisplayName("reversed and basic with same text produce different hashes")
    void reversedDifferentFromBasic() {
      NoteContent.Basic basic = new NoteContent.Basic("Front", "Back");
      NoteContent.Reversed reversed = new NoteContent.Reversed("Front", "Back");

      assertThat(NoteContentHasher.hash(basic)).isNotEqualTo(NoteContentHasher.hash(reversed));
    }
  }

  // ---------------------------------------------------------------
  // Cloze
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("Cloze notes")
  class ClozeNotes {

    @Test
    @DisplayName("same cloze text = same hash")
    void sameHash() {
      NoteContent.Cloze a = new NoteContent.Cloze("Java {{c1::is}} a language.");
      NoteContent.Cloze b = new NoteContent.Cloze("Java {{c1::is}} a language.");

      assertThat(NoteContentHasher.hash(a)).isEqualTo(NoteContentHasher.hash(b));
    }

    @Test
    @DisplayName("NoteImport cloze matches NoteContent hash")
    void importMatchesDomain() {
      NoteContent.Cloze domain = new NoteContent.Cloze("Java {{c1::is}} a language.");
      NoteImport imported =
          new NoteImport(
              "cloze",
              null,
              null,
              "Java {{c1::is}} a language.",
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null);

      assertThat(NoteContentHasher.hash(domain)).isEqualTo(NoteContentHasher.hash(imported));
    }
  }

  // ---------------------------------------------------------------
  // Multiple Choice
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("Multiple choice notes")
  class MultipleChoiceNotes {

    @Test
    @DisplayName("same MC note produces same hash")
    void sameHash() {
      var opts =
          List.of(
              new NoteContent.MultipleChoice.Option("A", "One"),
              new NoteContent.MultipleChoice.Option("B", "Two"),
              new NoteContent.MultipleChoice.Option("C", "Three"),
              new NoteContent.MultipleChoice.Option("D", "Four"));
      NoteContent.MultipleChoice a =
          new NoteContent.MultipleChoice("What?", opts, List.of("A"), null);
      NoteContent.MultipleChoice b =
          new NoteContent.MultipleChoice("What?", opts, List.of("A"), null);

      assertThat(NoteContentHasher.hash(a)).isEqualTo(NoteContentHasher.hash(b));
    }

    @Test
    @DisplayName("different correct key produces different hash")
    void differentCorrectKey() {
      var opts =
          List.of(
              new NoteContent.MultipleChoice.Option("A", "One"),
              new NoteContent.MultipleChoice.Option("B", "Two"),
              new NoteContent.MultipleChoice.Option("C", "Three"),
              new NoteContent.MultipleChoice.Option("D", "Four"));
      NoteContent.MultipleChoice a =
          new NoteContent.MultipleChoice("What?", opts, List.of("A"), null);
      NoteContent.MultipleChoice b =
          new NoteContent.MultipleChoice("What?", opts, List.of("B"), null);

      assertThat(NoteContentHasher.hash(a)).isNotEqualTo(NoteContentHasher.hash(b));
    }

    @Test
    @DisplayName("NoteImport multiple-choice matches NoteContent hash")
    void importMatchesDomain() {
      var domainOpts =
          List.of(
              new NoteContent.MultipleChoice.Option("A", "One"),
              new NoteContent.MultipleChoice.Option("B", "Two"),
              new NoteContent.MultipleChoice.Option("C", "Three"),
              new NoteContent.MultipleChoice.Option("D", "Four"));
      NoteContent.MultipleChoice domain =
          new NoteContent.MultipleChoice("What?", domainOpts, List.of("A"), null);

      var importOpts =
          List.of(
              new OptionImport("A", "One"),
              new OptionImport("B", "Two"),
              new OptionImport("C", "Three"),
              new OptionImport("D", "Four"));
      NoteImport imported =
          new NoteImport(
              "multiple-choice",
              null,
              null,
              null,
              "What?",
              importOpts,
              List.of("A"),
              null,
              null,
              null,
              null,
              null,
              null);

      assertThat(NoteContentHasher.hash(domain)).isEqualTo(NoteContentHasher.hash(imported));
    }
  }

  // ---------------------------------------------------------------
  // Free Text
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("Free-text notes")
  class FreeTextNotes {

    @Test
    @DisplayName("same free-text note = same hash")
    void sameHash() {
      NoteContent.FreeText a = new NoteContent.FreeText("Explain X.", "X means Y.", null);
      NoteContent.FreeText b = new NoteContent.FreeText("Explain X.", "X means Y.", null);

      assertThat(NoteContentHasher.hash(a)).isEqualTo(NoteContentHasher.hash(b));
    }

    @Test
    @DisplayName("NoteImport free-text matches domain hash")
    void importMatchesDomain() {
      NoteContent.FreeText domain = new NoteContent.FreeText("Explain X.", "X means Y.", null);
      NoteImport imported =
          new NoteImport(
              "free-text",
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              "Explain X.",
              "X means Y.",
              null,
              null,
              null);

      assertThat(NoteContentHasher.hash(domain)).isEqualTo(NoteContentHasher.hash(imported));
    }
  }

  // ---------------------------------------------------------------
  // Duplicate detection
  // ---------------------------------------------------------------

  @Test
  @DisplayName("duplicate note import: two notes with identical content produce same hash")
  void duplicateImportDetected() {
    NoteImport first =
        new NoteImport(
            "basic",
            "What is OOP?",
            "Object Oriented Programming.",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of("oop"),
            null);
    NoteImport second =
        new NoteImport(
            "basic",
            "What is OOP?",
            "Object Oriented Programming.",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    // Same logical content (tags ignored in hash) → same hash
    assertThat(NoteContentHasher.hash(first)).isEqualTo(NoteContentHasher.hash(second));
  }
}
