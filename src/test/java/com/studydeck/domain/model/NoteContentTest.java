package com.studydeck.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studydeck.domain.exception.DomainValidationException;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for all five NoteContent sealed variants and their invariants. */
class NoteContentTest {

  // ---------------------------------------------------------------
  // BASIC
  // ---------------------------------------------------------------
  @Nested
  class BasicContentTests {

    @Test
    void validBasicContentCreates() {
      NoteContent.Basic content = new NoteContent.Basic("What is Java?", "A JVM language");
      assertThat(content.front()).isEqualTo("What is Java?");
      assertThat(content.back()).isEqualTo("A JVM language");
    }

    @Test
    void blankFrontIsRejected() {
      assertThatThrownBy(() -> new NoteContent.Basic("  ", "back"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("front");
    }

    @Test
    void blankBackIsRejected() {
      assertThatThrownBy(() -> new NoteContent.Basic("front", ""))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("back");
    }

    @Test
    void nullFrontIsRejected() {
      assertThatThrownBy(() -> new NoteContent.Basic(null, "back"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("front");
    }

    @Test
    void frontTooLongIsRejected() {
      String longFront = "x".repeat(1001);
      assertThatThrownBy(() -> new NoteContent.Basic(longFront, "back"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("front");
    }

    @Test
    void backTooLongIsRejected() {
      String longBack = "x".repeat(5001);
      assertThatThrownBy(() -> new NoteContent.Basic("front", longBack))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("back");
    }
  }

  // ---------------------------------------------------------------
  // REVERSED
  // ---------------------------------------------------------------
  @Nested
  class ReversedContentTests {

    @Test
    void validReversedContentCreates() {
      NoteContent.Reversed content = new NoteContent.Reversed("Capital of France?", "Paris");
      assertThat(content.front()).isEqualTo("Capital of France?");
      assertThat(content.back()).isEqualTo("Paris");
    }

    @Test
    void blankFrontIsRejected() {
      assertThatThrownBy(() -> new NoteContent.Reversed("", "back"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("front");
    }

    @Test
    void blankBackIsRejected() {
      assertThatThrownBy(() -> new NoteContent.Reversed("front", "  "))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("back");
    }
  }

  // ---------------------------------------------------------------
  // CLOZE
  // ---------------------------------------------------------------
  @Nested
  class ClozeContentTests {

    @Test
    void validSingleClozeDeletion() {
      NoteContent.Cloze content = new NoteContent.Cloze("The capital of France is {{c1::Paris}}.");
      assertThat(content.text()).contains("{{c1::Paris}}");
    }

    @Test
    void validMultipleClozeDeletions() {
      NoteContent.Cloze content = new NoteContent.Cloze("{{c1::Java}} runs on the {{c2::JVM}}.");
      assertThat(content.text()).contains("{{c1::Java}}");
      assertThat(content.text()).contains("{{c2::JVM}}");
    }

    @Test
    void clozeTextWithNoDeletionMarkerIsRejected() {
      assertThatThrownBy(() -> new NoteContent.Cloze("No deletion marker here."))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("cloze");
    }

    @Test
    void blankClozeTextIsRejected() {
      assertThatThrownBy(() -> new NoteContent.Cloze("  "))
          .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void nullClozeTextIsRejected() {
      assertThatThrownBy(() -> new NoteContent.Cloze(null))
          .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void clozeTextTooLongIsRejected() {
      // Build a text longer than 5000 chars that still has a marker
      String longText = "{{c1::x}}" + "y".repeat(5000);
      assertThatThrownBy(() -> new NoteContent.Cloze(longText))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("text");
    }

    @Test
    void parseDeletionNumbersReturnsDistinctSortedSet() {
      NoteContent.Cloze content =
          new NoteContent.Cloze("{{c2::two}} and {{c1::one}} and {{c2::also-two}}");
      assertThat(content.parseDeletionNumbers()).containsExactly(1, 2);
    }

    @Test
    void parseDeletionNumbersSingleMarker() {
      NoteContent.Cloze content = new NoteContent.Cloze("Learn {{c1::Java}} today.");
      assertThat(content.parseDeletionNumbers()).containsExactly(1);
    }
  }

  // ---------------------------------------------------------------
  // MULTIPLE CHOICE
  // ---------------------------------------------------------------
  @Nested
  class MultipleChoiceContentTests {

    private List<NoteContent.MultipleChoice.Option> fourOptions() {
      return List.of(
          new NoteContent.MultipleChoice.Option("A", "Option A"),
          new NoteContent.MultipleChoice.Option("B", "Option B"),
          new NoteContent.MultipleChoice.Option("C", "Option C"),
          new NoteContent.MultipleChoice.Option("D", "Option D"));
    }

    private List<NoteContent.MultipleChoice.Option> fiveOptions() {
      return List.of(
          new NoteContent.MultipleChoice.Option("A", "Option A"),
          new NoteContent.MultipleChoice.Option("B", "Option B"),
          new NoteContent.MultipleChoice.Option("C", "Option C"),
          new NoteContent.MultipleChoice.Option("D", "Option D"),
          new NoteContent.MultipleChoice.Option("E", "Option E"));
    }

    @Test
    void validFourOptionMcq() {
      NoteContent.MultipleChoice content =
          new NoteContent.MultipleChoice("What is 2+2?", fourOptions(), List.of("A"), null);
      assertThat(content.question()).isEqualTo("What is 2+2?");
      assertThat(content.options()).hasSize(4);
      assertThat(content.correctOptionKeys()).containsExactly("A");
    }

    @Test
    void validFiveOptionMcq() {
      NoteContent.MultipleChoice content =
          new NoteContent.MultipleChoice("Question?", fiveOptions(), List.of("E"), null);
      assertThat(content.options()).hasSize(5);
    }

    @Test
    void tooFewOptionsIsRejected() {
      List<NoteContent.MultipleChoice.Option> three =
          List.of(
              new NoteContent.MultipleChoice.Option("A", "Option A"),
              new NoteContent.MultipleChoice.Option("B", "Option B"),
              new NoteContent.MultipleChoice.Option("C", "Option C"));
      assertThatThrownBy(
              () -> new NoteContent.MultipleChoice("Question?", three, List.of("A"), null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("options");
    }

    @Test
    void tooManyOptionsIsRejected() {
      List<NoteContent.MultipleChoice.Option> six =
          List.of(
              new NoteContent.MultipleChoice.Option("A", "A"),
              new NoteContent.MultipleChoice.Option("B", "B"),
              new NoteContent.MultipleChoice.Option("C", "C"),
              new NoteContent.MultipleChoice.Option("D", "D"),
              new NoteContent.MultipleChoice.Option("E", "E"),
              new NoteContent.MultipleChoice.Option("F", "F"));
      assertThatThrownBy(() -> new NoteContent.MultipleChoice("Question?", six, List.of("A"), null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("options");
    }

    @Test
    void noCorrectOptionIsRejected() {
      assertThatThrownBy(
              () -> new NoteContent.MultipleChoice("Question?", fourOptions(), List.of(), null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("correct");
    }

    @Test
    void correctOptionNotInOptionsListIsRejected() {
      assertThatThrownBy(
              () -> new NoteContent.MultipleChoice("Question?", fourOptions(), List.of("Z"), null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("correct");
    }

    @Test
    void blankQuestionIsRejected() {
      assertThatThrownBy(
              () -> new NoteContent.MultipleChoice("  ", fourOptions(), List.of("A"), null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("question");
    }

    @Test
    void blankOptionTextIsRejected() {
      assertThatThrownBy(() -> new NoteContent.MultipleChoice.Option("A", "  "))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("text");
    }

    @Test
    void optionKeyNotSingleUppercaseLetterIsRejected() {
      assertThatThrownBy(() -> new NoteContent.MultipleChoice.Option("a", "text"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("key");
    }

    @Test
    void explanationIsOptional() {
      NoteContent.MultipleChoice content =
          new NoteContent.MultipleChoice("Q?", fourOptions(), List.of("A"), "Some explanation");
      assertThat(content.explanation()).isEqualTo("Some explanation");
    }
  }

  // ---------------------------------------------------------------
  // FREE TEXT
  // ---------------------------------------------------------------
  @Nested
  class FreeTextContentTests {

    @Test
    void validFreeTextContent() {
      NoteContent.FreeText content =
          new NoteContent.FreeText("Explain JVM memory model", "Stack vs heap...", null);
      assertThat(content.prompt()).isEqualTo("Explain JVM memory model");
      assertThat(content.expectedAnswer()).isEqualTo("Stack vs heap...");
      assertThat(content.gradingGuidance()).isNull();
    }

    @Test
    void withGradingGuidance() {
      NoteContent.FreeText content =
          new NoteContent.FreeText("Prompt", "Expected", "Grade by coverage");
      assertThat(content.gradingGuidance()).isEqualTo("Grade by coverage");
    }

    @Test
    void blankPromptIsRejected() {
      assertThatThrownBy(() -> new NoteContent.FreeText("  ", "answer", null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("prompt");
    }

    @Test
    void blankExpectedAnswerIsRejected() {
      assertThatThrownBy(() -> new NoteContent.FreeText("prompt", "", null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("expectedAnswer");
    }

    @Test
    void promptTooLongIsRejected() {
      String longPrompt = "x".repeat(2001);
      assertThatThrownBy(() -> new NoteContent.FreeText(longPrompt, "answer", null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("prompt");
    }
  }
}
