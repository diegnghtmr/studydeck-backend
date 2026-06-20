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
import com.studydeck.domain.port.out.CardRepository;
import com.studydeck.domain.port.out.DeckRepository;
import com.studydeck.domain.port.out.NoteRepository;
import com.studydeck.domain.service.CardGenerator;
import com.studydeck.integration.AiTestConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for {@link CardPersistenceAdapter}.
 *
 * <p>Tests CardPayload JSONB round-trips for all payload types, and persistence operations.
 */
@Import(AiTestConfiguration.class)
@SpringBootTest
@Testcontainers
@Transactional
class CardPersistenceAdapterTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg17")
          .withDatabaseName("studydeck_test")
          .withUsername("studydeck")
          .withPassword("studydeck");

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
  }

  @Autowired private CardRepository cardRepository;

  @Autowired private NoteRepository noteRepository;

  @Autowired private DeckRepository deckRepository;

  @Autowired private jakarta.persistence.EntityManager em;

  private final CardGenerator cardGenerator = new CardGenerator();
  private final OwnerId alice = OwnerId.generate();
  private DeckId deckId;
  private NoteId noteId;

  @BeforeEach
  void setUp() {
    UUID userId = alice.value();
    em.createNativeQuery(
            "INSERT INTO user_account(id, email, display_name) VALUES (:id, :email, :name)"
                + " ON CONFLICT DO NOTHING")
        .setParameter("id", userId)
        .setParameter("email", userId + "@test.com")
        .setParameter("name", "Alice")
        .executeUpdate();

    deckId = DeckId.generate();
    deckRepository.save(Deck.create(deckId, alice, "Test Deck", null));

    noteId = NoteId.generate();
    noteRepository.save(Note.create(noteId, deckId, new NoteContent.Basic("Q", "A"), null));
    em.flush();
    em.clear();
  }

  @Nested
  @DisplayName("CardPayload JSONB round-trip")
  class JsonbPayloadRoundTrip {

    @Test
    @DisplayName("BasicPrompt/BasicAnswer round-trips correctly")
    void basicPayloadRoundTrip() {
      Card card =
          Card.create(
              CardId.generate(),
              noteId,
              NoteType.BASIC,
              "forward",
              0,
              new CardPayload.BasicPrompt("What is Java?"),
              new CardPayload.BasicAnswer("A programming language."));
      cardRepository.save(card);
      em.flush();
      em.clear();

      Card loaded = cardRepository.findById(card.getId()).orElseThrow();
      assertThat(loaded.getPromptPayload()).isInstanceOf(CardPayload.BasicPrompt.class);
      assertThat(((CardPayload.BasicPrompt) loaded.getPromptPayload()).front())
          .isEqualTo("What is Java?");
      assertThat(loaded.getAnswerPayload()).isInstanceOf(CardPayload.BasicAnswer.class);
      assertThat(((CardPayload.BasicAnswer) loaded.getAnswerPayload()).back())
          .isEqualTo("A programming language.");
    }

    @Test
    @DisplayName("ClozePrompt/ClozeAnswer round-trips correctly")
    void clozePayloadRoundTrip() {
      Card card =
          Card.create(
              CardId.generate(),
              noteId,
              NoteType.CLOZE,
              "cloze-1",
              0,
              new CardPayload.ClozePrompt(1, "Java is [...] typed."),
              new CardPayload.ClozeAnswer("Java is {{c1::statically}} typed.", "statically"));
      cardRepository.save(card);
      em.flush();
      em.clear();

      Card loaded = cardRepository.findById(card.getId()).orElseThrow();
      CardPayload.ClozePrompt prompt = (CardPayload.ClozePrompt) loaded.getPromptPayload();
      assertThat(prompt.deletionNumber()).isEqualTo(1);
      assertThat(prompt.maskedText()).isEqualTo("Java is [...] typed.");
      CardPayload.ClozeAnswer answer = (CardPayload.ClozeAnswer) loaded.getAnswerPayload();
      assertThat(answer.deletedText()).isEqualTo("statically");
    }

    @Test
    @DisplayName("McqPrompt/McqAnswer round-trips correctly")
    void mcqPayloadRoundTrip() {
      List<NoteContent.MultipleChoice.Option> options =
          List.of(
              new NoteContent.MultipleChoice.Option("A", "Java"),
              new NoteContent.MultipleChoice.Option("B", "Python"),
              new NoteContent.MultipleChoice.Option("C", "Ruby"),
              new NoteContent.MultipleChoice.Option("D", "C#"));
      Card card =
          Card.create(
              CardId.generate(),
              noteId,
              NoteType.MULTIPLE_CHOICE,
              "mcq",
              0,
              new CardPayload.McqPrompt("Which is JVM?", options),
              new CardPayload.McqAnswer(List.of("A"), "Java runs on JVM."));
      cardRepository.save(card);
      em.flush();
      em.clear();

      Card loaded = cardRepository.findById(card.getId()).orElseThrow();
      CardPayload.McqPrompt prompt = (CardPayload.McqPrompt) loaded.getPromptPayload();
      assertThat(prompt.question()).isEqualTo("Which is JVM?");
      assertThat(prompt.options()).hasSize(4);
      CardPayload.McqAnswer answer = (CardPayload.McqAnswer) loaded.getAnswerPayload();
      assertThat(answer.correctOptionKeys()).containsExactly("A");
    }

    @Test
    @DisplayName("FreeTextPrompt/FreeTextAnswer round-trips correctly")
    void freeTextPayloadRoundTrip() {
      Card card =
          Card.create(
              CardId.generate(),
              noteId,
              NoteType.FREE_TEXT,
              "free-text",
              0,
              new CardPayload.FreeTextPrompt("Explain DI."),
              new CardPayload.FreeTextAnswer("Injecting dependencies.", "Award 1 point."));
      cardRepository.save(card);
      em.flush();
      em.clear();

      Card loaded = cardRepository.findById(card.getId()).orElseThrow();
      assertThat(((CardPayload.FreeTextPrompt) loaded.getPromptPayload()).prompt())
          .isEqualTo("Explain DI.");
      assertThat(((CardPayload.FreeTextAnswer) loaded.getAnswerPayload()).expectedAnswer())
          .isEqualTo("Injecting dependencies.");
    }
  }

  @Nested
  @DisplayName("CardGenerator integration with persistence")
  class CardGeneratorPersistence {

    @Test
    @DisplayName("saves all cards generated from a REVERSED note")
    void savesAllReversedCards() {
      Note note = Note.create(noteId, deckId, new NoteContent.Reversed("Front", "Back"), null);
      List<Card> cards = cardGenerator.generate(note);

      cardRepository.saveAll(cards);
      em.flush();
      em.clear();

      List<Card> loaded = cardRepository.findByNoteId(noteId);
      assertThat(loaded).hasSize(2);
      assertThat(loaded.stream().map(Card::getOrdinal)).containsExactlyInAnyOrder(0, 1);
    }

    @Test
    @DisplayName("saves all cards generated from a CLOZE note with 2 deletions")
    void savesAllClozeCards() {
      Note note =
          Note.create(
              noteId, deckId, new NoteContent.Cloze("{{c1::Java}} is {{c2::typed}}."), null);
      List<Card> cards = cardGenerator.generate(note);

      cardRepository.saveAll(cards);
      em.flush();
      em.clear();

      List<Card> loaded = cardRepository.findByNoteId(noteId);
      assertThat(loaded).hasSize(2);
    }
  }

  @Nested
  @DisplayName("suspend flag")
  class SuspendTests {

    @Test
    @DisplayName("suspended flag persists correctly")
    void suspendedFlagPersists() {
      Card card =
          Card.create(
              CardId.generate(),
              noteId,
              NoteType.BASIC,
              "forward",
              0,
              new CardPayload.BasicPrompt("Q"),
              new CardPayload.BasicAnswer("A"));
      cardRepository.save(card);
      em.flush();
      em.clear();

      card.suspend();
      cardRepository.save(card);
      em.flush();
      em.clear();

      Card loaded = cardRepository.findById(card.getId()).orElseThrow();
      assertThat(loaded.isSuspended()).isTrue();
    }
  }

  @Nested
  @DisplayName("pagination and filters")
  class PaginationTests {

    @Test
    @DisplayName("findAll paginates correctly")
    void paginatesCorrectly() {
      for (int i = 0; i < 5; i++) {
        cardRepository.save(
            Card.create(
                CardId.generate(),
                noteId,
                NoteType.BASIC,
                "forward",
                i,
                new CardPayload.BasicPrompt("Q" + i),
                new CardPayload.BasicAnswer("A" + i)));
      }
      em.flush();
      em.clear();

      List<Card> page0 = cardRepository.findAll(deckId, null, 0, 2);
      List<Card> page1 = cardRepository.findAll(deckId, null, 2, 2);

      assertThat(page0).hasSize(2);
      assertThat(page1).hasSize(2);
    }

    @Test
    @DisplayName("countAll returns correct total for deckId")
    void countAllByDeck() {
      for (int i = 0; i < 3; i++) {
        cardRepository.save(
            Card.create(
                CardId.generate(),
                noteId,
                NoteType.BASIC,
                "forward",
                i,
                new CardPayload.BasicPrompt("Q" + i),
                new CardPayload.BasicAnswer("A" + i)));
      }
      em.flush();
      em.clear();

      long count = cardRepository.countAll(deckId, null);
      assertThat(count).isEqualTo(3);
    }
  }
}
