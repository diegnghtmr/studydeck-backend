package com.studydeck.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studydeck.domain.model.Card;
import com.studydeck.domain.model.CardPayload;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.Note;
import com.studydeck.domain.model.NoteContent;
import com.studydeck.domain.model.NoteId;
import com.studydeck.domain.model.NoteType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * TDD tests for CardGenerator domain service.
 *
 * <p>Rules under test:
 *
 * <ul>
 *   <li>BASIC → 1 card (forward)
 *   <li>REVERSED → 2 cards (forward + backward), ordinals 0 and 1
 *   <li>CLOZE → 1 card per distinct cloze number, each masking only its own deletions
 *   <li>MULTIPLE_CHOICE → 1 card
 *   <li>FREE_TEXT → 1 card
 * </ul>
 */
class CardGeneratorTest {

  private CardGenerator generator;
  private final DeckId deckId = DeckId.generate();

  @BeforeEach
  void setUp() {
    generator = new CardGenerator();
  }

  // ---------------------------------------------------------------
  // BASIC
  // ---------------------------------------------------------------
  @Nested
  class BasicNoteCards {

    @Test
    void basicNoteProducesExactlyOneCard() {
      Note note = aNote(new NoteContent.Basic("What is JVM?", "Java Virtual Machine"));
      List<Card> cards = generator.generate(note);
      assertThat(cards).hasSize(1);
    }

    @Test
    void basicCardHasForwardPromptAndAnswer() {
      Note note = aNote(new NoteContent.Basic("What is JVM?", "Java Virtual Machine"));
      Card card = generator.generate(note).get(0);

      assertThat(card.getNoteType()).isEqualTo(NoteType.BASIC);
      assertThat(card.getOrdinal()).isEqualTo(0);
      assertThat(card.getCardVariant()).isEqualTo("forward");

      CardPayload.BasicPrompt prompt = (CardPayload.BasicPrompt) card.getPromptPayload();
      CardPayload.BasicAnswer answer = (CardPayload.BasicAnswer) card.getAnswerPayload();

      assertThat(prompt.front()).isEqualTo("What is JVM?");
      assertThat(answer.back()).isEqualTo("Java Virtual Machine");
    }

    @Test
    void basicCardIsNotSuspendedByDefault() {
      Note note = aNote(new NoteContent.Basic("Q", "A"));
      Card card = generator.generate(note).get(0);
      assertThat(card.isSuspended()).isFalse();
    }

    @Test
    void basicCardHasNoteIdSet() {
      Note note = aNote(new NoteContent.Basic("Q", "A"));
      Card card = generator.generate(note).get(0);
      assertThat(card.getNoteId()).isEqualTo(note.getId());
    }
  }

  // ---------------------------------------------------------------
  // REVERSED
  // ---------------------------------------------------------------
  @Nested
  class ReversedNoteCards {

    @Test
    void reversedNoteProducesExactlyTwoCards() {
      Note note = aNote(new NoteContent.Reversed("Capital of France?", "Paris"));
      List<Card> cards = generator.generate(note);
      assertThat(cards).hasSize(2);
    }

    @Test
    void reversedCardsHaveDistinctOrdinals() {
      Note note = aNote(new NoteContent.Reversed("Q", "A"));
      List<Card> cards = generator.generate(note);
      assertThat(cards.get(0).getOrdinal()).isEqualTo(0);
      assertThat(cards.get(1).getOrdinal()).isEqualTo(1);
    }

    @Test
    void firstReversedCardIsForward() {
      Note note = aNote(new NoteContent.Reversed("Front", "Back"));
      Card forward = generator.generate(note).get(0);

      assertThat(forward.getCardVariant()).isEqualTo("forward");
      CardPayload.BasicPrompt prompt = (CardPayload.BasicPrompt) forward.getPromptPayload();
      CardPayload.BasicAnswer answer = (CardPayload.BasicAnswer) forward.getAnswerPayload();
      assertThat(prompt.front()).isEqualTo("Front");
      assertThat(answer.back()).isEqualTo("Back");
    }

    @Test
    void secondReversedCardIsBackward() {
      Note note = aNote(new NoteContent.Reversed("Front", "Back"));
      Card backward = generator.generate(note).get(1);

      assertThat(backward.getCardVariant()).isEqualTo("reverse");
      CardPayload.BasicPrompt prompt = (CardPayload.BasicPrompt) backward.getPromptPayload();
      CardPayload.BasicAnswer answer = (CardPayload.BasicAnswer) backward.getAnswerPayload();
      // In reverse: the "front" is the back content, the answer reveals front
      assertThat(prompt.front()).isEqualTo("Back");
      assertThat(answer.back()).isEqualTo("Front");
    }

    @Test
    void reversedCardsHaveDistinctIds() {
      Note note = aNote(new NoteContent.Reversed("Q", "A"));
      List<Card> cards = generator.generate(note);
      assertThat(cards.get(0).getId()).isNotEqualTo(cards.get(1).getId());
    }
  }

  // ---------------------------------------------------------------
  // CLOZE
  // ---------------------------------------------------------------
  @Nested
  class ClozeNoteCards {

    @Test
    void singleDeletionProducesOneCard() {
      Note note = aNote(new NoteContent.Cloze("The capital of France is {{c1::Paris}}."));
      List<Card> cards = generator.generate(note);
      assertThat(cards).hasSize(1);
    }

    @Test
    void twoDeletionNumbersProduceTwoCards() {
      Note note = aNote(new NoteContent.Cloze("{{c1::Java}} runs on the {{c2::JVM}}."));
      List<Card> cards = generator.generate(note);
      assertThat(cards).hasSize(2);
    }

    @Test
    void clozeCardsAreOrderedByDeletionNumber() {
      Note note = aNote(new NoteContent.Cloze("{{c2::two}} and {{c1::one}}."));
      List<Card> cards = generator.generate(note);
      assertThat(cards.get(0).getOrdinal()).isEqualTo(0);
      assertThat(cards.get(1).getOrdinal()).isEqualTo(1);

      CardPayload.ClozePrompt prompt0 = (CardPayload.ClozePrompt) cards.get(0).getPromptPayload();
      CardPayload.ClozePrompt prompt1 = (CardPayload.ClozePrompt) cards.get(1).getPromptPayload();
      // card-0 masks c1, card-1 masks c2
      assertThat(prompt0.deletionNumber()).isEqualTo(1);
      assertThat(prompt1.deletionNumber()).isEqualTo(2);
    }

    @Test
    void clozeCardMasksOnlyItsOwnDeletion() {
      Note note = aNote(new NoteContent.Cloze("{{c1::Java}} runs on the {{c2::JVM}}."));
      List<Card> cards = generator.generate(note);

      // Card for c1 should mask [c1] but NOT [c2]
      CardPayload.ClozePrompt promptC1 = (CardPayload.ClozePrompt) cards.get(0).getPromptPayload();
      assertThat(promptC1.maskedText()).contains("[...]");
      assertThat(promptC1.maskedText()).contains("JVM"); // c2 is visible

      // Card for c2 should mask [c2] but NOT [c1]
      CardPayload.ClozePrompt promptC2 = (CardPayload.ClozePrompt) cards.get(1).getPromptPayload();
      assertThat(promptC2.maskedText()).contains("[...]");
      assertThat(promptC2.maskedText()).contains("Java"); // c1 is visible
    }

    @Test
    void clozeAnswerPayloadContainsFullText() {
      Note note = aNote(new NoteContent.Cloze("{{c1::Paris}} is the capital."));
      Card card = generator.generate(note).get(0);
      CardPayload.ClozeAnswer answer = (CardPayload.ClozeAnswer) card.getAnswerPayload();
      assertThat(answer.fullText()).isEqualTo("{{c1::Paris}} is the capital.");
      assertThat(answer.deletedText()).isEqualTo("Paris");
    }

    @Test
    void multipleSameNumberSharedDeletionsMaskedTogether() {
      // c1 appears twice — both should be masked in the single c1 card
      Note note =
          aNote(new NoteContent.Cloze("{{c1::Java}} and {{c1::Kotlin}} both run on {{c2::JVM}}."));
      List<Card> cards = generator.generate(note);
      // Still 2 cards (c1 and c2)
      assertThat(cards).hasSize(2);

      CardPayload.ClozePrompt promptC1 = (CardPayload.ClozePrompt) cards.get(0).getPromptPayload();
      // Both c1 occurrences are masked
      assertThat(promptC1.maskedText()).doesNotContain("Java");
      assertThat(promptC1.maskedText()).doesNotContain("Kotlin");
      // c2 is still visible
      assertThat(promptC1.maskedText()).contains("JVM");
    }

    @Test
    void clozeVariantNameContainsDeletionNumber() {
      Note note = aNote(new NoteContent.Cloze("{{c1::Answer}}."));
      Card card = generator.generate(note).get(0);
      assertThat(card.getCardVariant()).isEqualTo("cloze-1");
    }

    @Test
    void clozeCardWithThreeDeletionNumbers() {
      Note note = aNote(new NoteContent.Cloze("{{c1::A}}, {{c2::B}}, {{c3::C}}"));
      assertThat(generator.generate(note)).hasSize(3);
    }
  }

  // ---------------------------------------------------------------
  // MULTIPLE CHOICE
  // ---------------------------------------------------------------
  @Nested
  class MultipleChoiceNoteCards {

    @Test
    void mcqProducesExactlyOneCard() {
      Note note = aNote(mcqContent());
      List<Card> cards = generator.generate(note);
      assertThat(cards).hasSize(1);
    }

    @Test
    void mcqCardVariantIsMcq() {
      Note note = aNote(mcqContent());
      Card card = generator.generate(note).get(0);
      assertThat(card.getCardVariant()).isEqualTo("mcq");
    }

    @Test
    void mcqPromptContainsQuestionAndOptions() {
      Note note = aNote(mcqContent());
      Card card = generator.generate(note).get(0);

      CardPayload.McqPrompt prompt = (CardPayload.McqPrompt) card.getPromptPayload();
      assertThat(prompt.question()).isEqualTo("What is 2+2?");
      assertThat(prompt.options()).hasSize(4);
    }

    @Test
    void mcqAnswerContainsCorrectOptionKey() {
      Note note = aNote(mcqContent());
      Card card = generator.generate(note).get(0);

      CardPayload.McqAnswer answer = (CardPayload.McqAnswer) card.getAnswerPayload();
      assertThat(answer.correctOptionKeys()).containsExactly("A");
    }

    @Test
    void mcqWithFiveOptionsProducesOneCard() {
      List<NoteContent.MultipleChoice.Option> fiveOptions =
          List.of(
              new NoteContent.MultipleChoice.Option("A", "Opt A"),
              new NoteContent.MultipleChoice.Option("B", "Opt B"),
              new NoteContent.MultipleChoice.Option("C", "Opt C"),
              new NoteContent.MultipleChoice.Option("D", "Opt D"),
              new NoteContent.MultipleChoice.Option("E", "Opt E"));
      NoteContent.MultipleChoice content =
          new NoteContent.MultipleChoice("Question?", fiveOptions, List.of("E"), null);
      Note note = aNote(content);
      assertThat(generator.generate(note)).hasSize(1);
    }

    private NoteContent.MultipleChoice mcqContent() {
      return new NoteContent.MultipleChoice(
          "What is 2+2?",
          List.of(
              new NoteContent.MultipleChoice.Option("A", "4"),
              new NoteContent.MultipleChoice.Option("B", "3"),
              new NoteContent.MultipleChoice.Option("C", "5"),
              new NoteContent.MultipleChoice.Option("D", "22")),
          List.of("A"),
          null);
    }
  }

  // ---------------------------------------------------------------
  // FREE TEXT
  // ---------------------------------------------------------------
  @Nested
  class FreeTextNoteCards {

    @Test
    void freeTextProducesExactlyOneCard() {
      Note note = aNote(new NoteContent.FreeText("Explain GC", "Stop-the-world...", null));
      assertThat(generator.generate(note)).hasSize(1);
    }

    @Test
    void freeTextCardVariantIsFreeText() {
      Note note = aNote(new NoteContent.FreeText("Explain GC", "Stop-the-world...", null));
      Card card = generator.generate(note).get(0);
      assertThat(card.getCardVariant()).isEqualTo("free-text");
    }

    @Test
    void freeTextPromptContainsPromptField() {
      Note note = aNote(new NoteContent.FreeText("What is polymorphism?", "...", null));
      Card card = generator.generate(note).get(0);
      CardPayload.FreeTextPrompt prompt = (CardPayload.FreeTextPrompt) card.getPromptPayload();
      assertThat(prompt.prompt()).isEqualTo("What is polymorphism?");
    }

    @Test
    void freeTextAnswerContainsExpectedAnswerAndGuidance() {
      Note note =
          aNote(
              new NoteContent.FreeText(
                  "What is polymorphism?", "Multiple forms", "Check for examples"));
      Card card = generator.generate(note).get(0);
      CardPayload.FreeTextAnswer answer = (CardPayload.FreeTextAnswer) card.getAnswerPayload();
      assertThat(answer.expectedAnswer()).isEqualTo("Multiple forms");
      assertThat(answer.gradingGuidance()).isEqualTo("Check for examples");
    }
  }

  // ---------------------------------------------------------------
  // NULL guard
  // ---------------------------------------------------------------
  @Test
  void nullNoteThrowsNullPointerException() {
    assertThatThrownBy(() -> generator.generate(null)).isInstanceOf(NullPointerException.class);
  }

  // ---------------------------------------------------------------
  // Helper
  // ---------------------------------------------------------------
  private Note aNote(NoteContent content) {
    return Note.create(NoteId.generate(), deckId, content, null);
  }
}
