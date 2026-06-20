package com.studydeck.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.studydeck.domain.model.Card;
import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.CardPayload;
import com.studydeck.domain.model.Deck;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.Note;
import com.studydeck.domain.model.NoteContent;
import com.studydeck.domain.model.NoteId;
import com.studydeck.domain.model.NoteType;
import com.studydeck.domain.model.OwnerId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PersistenceMapper} — pure Java, no Spring context.
 *
 * <p>Validates domain ↔ JPA entity mapping and JSONB serialization round-trips.
 */
class PersistenceMapperTest {

  private final PersistenceMapper mapper = new PersistenceMapper();

  private final DeckId deckId = DeckId.generate();
  private final OwnerId ownerId = OwnerId.generate();
  private final NoteId noteId = NoteId.generate();

  @Nested
  @DisplayName("Deck mapping")
  class DeckMappingTests {

    @Test
    @DisplayName("toJpa maps all Deck fields correctly")
    void toJpaMapsAllFields() {
      Deck deck =
          Deck.create(deckId, ownerId, "Java Basics", "Core concepts", List.of("java"), 0.85);
      DeckJpaEntity entity = mapper.toJpa(deck);

      assertThat(entity.getId()).isEqualTo(deckId.value());
      assertThat(entity.getOwnerId()).isEqualTo(ownerId.value());
      assertThat(entity.getTitle()).isEqualTo("Java Basics");
      assertThat(entity.getDescription()).isEqualTo("Core concepts");
      assertThat(entity.getTags()).containsExactly("java");
      assertThat(entity.getDefaultDesiredRetention()).isEqualTo(0.85);
      assertThat(entity.isArchived()).isFalse();
    }

    @Test
    @DisplayName("toDomain reconstructs Deck from entity")
    void toDomainReconstructs() {
      Deck deck = Deck.create(deckId, ownerId, "Java Basics", null, List.of("java"), 0.9);
      DeckJpaEntity entity = mapper.toJpa(deck);
      Deck reconstructed = mapper.toDomain(entity);

      assertThat(reconstructed.getId()).isEqualTo(deckId);
      assertThat(reconstructed.getTitle()).isEqualTo("Java Basics");
      assertThat(reconstructed.getTags()).containsExactly("java");
      assertThat(reconstructed.getDefaultDesiredRetention()).isEqualTo(0.9);
    }
  }

  @Nested
  @DisplayName("NoteContent JSONB serialization")
  class NoteContentSerializationTests {

    @Test
    @DisplayName("Basic NoteContent survives toJpa → toDomain round-trip")
    void basicRoundTrip() {
      NoteContent.Basic content = new NoteContent.Basic("Front text", "Back text");
      Note note = Note.create(noteId, deckId, content, List.of("tag1"));
      NoteJpaEntity entity = mapper.toJpa(note);
      Note restored = mapper.toDomain(entity);

      assertThat(restored.getNoteType()).isEqualTo(NoteType.BASIC);
      NoteContent.Basic r = (NoteContent.Basic) restored.getContent();
      assertThat(r.front()).isEqualTo("Front text");
      assertThat(r.back()).isEqualTo("Back text");
      assertThat(restored.getTags()).containsExactly("tag1");
    }

    @Test
    @DisplayName("Cloze NoteContent survives round-trip")
    void clozeRoundTrip() {
      NoteContent.Cloze content = new NoteContent.Cloze("{{c1::Java}} runs on {{c2::JVM}}.");
      Note note = Note.create(noteId, deckId, content, null);
      NoteJpaEntity entity = mapper.toJpa(note);
      Note restored = mapper.toDomain(entity);

      NoteContent.Cloze r = (NoteContent.Cloze) restored.getContent();
      assertThat(r.text()).isEqualTo("{{c1::Java}} runs on {{c2::JVM}}.");
    }

    @Test
    @DisplayName("MultipleChoice NoteContent survives round-trip including option keys")
    void multipleChoiceRoundTrip() {
      NoteContent.MultipleChoice content =
          new NoteContent.MultipleChoice(
              "Best JVM language?",
              List.of(
                  new NoteContent.MultipleChoice.Option("A", "Java"),
                  new NoteContent.MultipleChoice.Option("B", "Scala"),
                  new NoteContent.MultipleChoice.Option("C", "Ruby"),
                  new NoteContent.MultipleChoice.Option("D", "Go")),
              List.of("A"),
              null);
      Note note = Note.create(noteId, deckId, content, null);
      NoteJpaEntity entity = mapper.toJpa(note);
      Note restored = mapper.toDomain(entity);

      NoteContent.MultipleChoice r = (NoteContent.MultipleChoice) restored.getContent();
      assertThat(r.options()).hasSize(4);
      assertThat(r.correctOptionKeys()).containsExactly("A");
      assertThat(r.explanation()).isNull();
    }

    @Test
    @DisplayName("FreeText NoteContent survives round-trip")
    void freeTextRoundTrip() {
      NoteContent.FreeText content =
          new NoteContent.FreeText(
              "Explain SOLID.", "Five principles of OOP.", "2 pts per principle");
      Note note = Note.create(noteId, deckId, content, null);
      NoteJpaEntity entity = mapper.toJpa(note);
      Note restored = mapper.toDomain(entity);

      NoteContent.FreeText r = (NoteContent.FreeText) restored.getContent();
      assertThat(r.prompt()).isEqualTo("Explain SOLID.");
      assertThat(r.gradingGuidance()).isEqualTo("2 pts per principle");
    }
  }

  @Nested
  @DisplayName("CardPayload JSONB serialization")
  class CardPayloadSerializationTests {

    @Test
    @DisplayName("BasicPrompt/BasicAnswer round-trip preserves @type discriminator")
    void basicPayloadRoundTrip() {
      Card card =
          Card.create(
              CardId.generate(),
              noteId,
              NoteType.BASIC,
              "forward",
              0,
              new CardPayload.BasicPrompt("Q"),
              new CardPayload.BasicAnswer("A"));
      CardJpaEntity entity = mapper.toJpa(card);
      Card restored = mapper.toDomain(entity);

      assertThat(restored.getPromptPayload()).isInstanceOf(CardPayload.BasicPrompt.class);
      assertThat(((CardPayload.BasicPrompt) restored.getPromptPayload()).front()).isEqualTo("Q");
      assertThat(restored.getAnswerPayload()).isInstanceOf(CardPayload.BasicAnswer.class);
      assertThat(((CardPayload.BasicAnswer) restored.getAnswerPayload()).back()).isEqualTo("A");
    }

    @Test
    @DisplayName("McqPrompt/McqAnswer round-trip preserves nested options list")
    void mcqPayloadRoundTrip() {
      List<NoteContent.MultipleChoice.Option> opts =
          List.of(
              new NoteContent.MultipleChoice.Option("A", "Opt A"),
              new NoteContent.MultipleChoice.Option("B", "Opt B"),
              new NoteContent.MultipleChoice.Option("C", "Opt C"),
              new NoteContent.MultipleChoice.Option("D", "Opt D"));
      Card card =
          Card.create(
              CardId.generate(),
              noteId,
              NoteType.MULTIPLE_CHOICE,
              "mcq",
              0,
              new CardPayload.McqPrompt("Question?", opts),
              new CardPayload.McqAnswer(List.of("A"), "Explanation here."));
      CardJpaEntity entity = mapper.toJpa(card);
      Card restored = mapper.toDomain(entity);

      CardPayload.McqPrompt prompt = (CardPayload.McqPrompt) restored.getPromptPayload();
      assertThat(prompt.options()).hasSize(4);
      CardPayload.McqAnswer answer = (CardPayload.McqAnswer) restored.getAnswerPayload();
      assertThat(answer.correctOptionKeys()).containsExactly("A");
    }

    @Test
    @DisplayName("ClozePrompt/ClozeAnswer round-trip preserves deletion number")
    void clozePayloadRoundTrip() {
      Card card =
          Card.create(
              CardId.generate(),
              noteId,
              NoteType.CLOZE,
              "cloze-2",
              1,
              new CardPayload.ClozePrompt(2, "[...] is statically typed."),
              new CardPayload.ClozeAnswer("Java is statically typed.", "Java"));
      CardJpaEntity entity = mapper.toJpa(card);
      Card restored = mapper.toDomain(entity);

      CardPayload.ClozePrompt p = (CardPayload.ClozePrompt) restored.getPromptPayload();
      assertThat(p.deletionNumber()).isEqualTo(2);
    }
  }
}
