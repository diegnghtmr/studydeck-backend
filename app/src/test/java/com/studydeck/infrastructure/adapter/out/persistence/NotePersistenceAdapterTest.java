package com.studydeck.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.studydeck.domain.model.Deck;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.Note;
import com.studydeck.domain.model.NoteContent;
import com.studydeck.domain.model.NoteId;
import com.studydeck.domain.model.NoteType;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.out.DeckRepository;
import com.studydeck.domain.port.out.NoteRepository;
import com.studydeck.integration.AiTestConfiguration;
import java.util.List;
import java.util.Optional;
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
 * Integration test for {@link NotePersistenceAdapter}.
 *
 * <p>Tests JSONB round-trips for all 5 NoteContent sealed variants.
 */
@Import(AiTestConfiguration.class)
@SpringBootTest
@Testcontainers
@Transactional
class NotePersistenceAdapterTest {

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

  @Autowired private NoteRepository noteRepository;

  @Autowired private DeckRepository deckRepository;

  @Autowired private jakarta.persistence.EntityManager em;

  private final OwnerId alice = OwnerId.generate();
  private DeckId deckId;

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
    em.flush();
    em.clear();
  }

  private Optional<Note> findById(NoteId id) {
    return noteRepository.findById(id);
  }

  @Nested
  @DisplayName("JSONB round-trip for all 5 NoteContent types")
  class JsonbRoundTrip {

    @Test
    @DisplayName("BASIC note content round-trips correctly")
    void basicNoteRoundTrip() {
      NoteContent.Basic content = new NoteContent.Basic("What is Java?", "A programming language.");
      Note note = Note.create(NoteId.generate(), deckId, content, List.of("java"));

      noteRepository.save(note);
      em.flush();
      em.clear();
      Note loaded = findById(note.getId()).orElseThrow();

      assertThat(loaded.getNoteType()).isEqualTo(NoteType.BASIC);
      NoteContent.Basic loadedContent = (NoteContent.Basic) loaded.getContent();
      assertThat(loadedContent.front()).isEqualTo("What is Java?");
      assertThat(loadedContent.back()).isEqualTo("A programming language.");
      assertThat(loaded.getTags()).containsExactly("java");
    }

    @Test
    @DisplayName("REVERSED note content round-trips correctly")
    void reversedNoteRoundTrip() {
      NoteContent.Reversed content = new NoteContent.Reversed("Capital of France?", "Paris");
      Note note = Note.create(NoteId.generate(), deckId, content, null);

      noteRepository.save(note);
      em.flush();
      em.clear();
      Note loaded = findById(note.getId()).orElseThrow();

      assertThat(loaded.getNoteType()).isEqualTo(NoteType.REVERSED);
      NoteContent.Reversed loadedContent = (NoteContent.Reversed) loaded.getContent();
      assertThat(loadedContent.front()).isEqualTo("Capital of France?");
      assertThat(loadedContent.back()).isEqualTo("Paris");
    }

    @Test
    @DisplayName("CLOZE note content round-trips correctly")
    void clozeNoteRoundTrip() {
      NoteContent.Cloze content =
          new NoteContent.Cloze("Java is {{c1::object-oriented}} and {{c2::statically typed}}.");
      Note note = Note.create(NoteId.generate(), deckId, content, null);

      noteRepository.save(note);
      em.flush();
      em.clear();
      Note loaded = findById(note.getId()).orElseThrow();

      assertThat(loaded.getNoteType()).isEqualTo(NoteType.CLOZE);
      NoteContent.Cloze loadedContent = (NoteContent.Cloze) loaded.getContent();
      assertThat(loadedContent.text())
          .isEqualTo("Java is {{c1::object-oriented}} and {{c2::statically typed}}.");
      assertThat(loadedContent.parseDeletionNumbers()).containsExactly(1, 2);
    }

    @Test
    @DisplayName("MULTIPLE_CHOICE note content round-trips correctly")
    void multipleChoiceNoteRoundTrip() {
      NoteContent.MultipleChoice content =
          new NoteContent.MultipleChoice(
              "Which is a JVM language?",
              List.of(
                  new NoteContent.MultipleChoice.Option("A", "Java"),
                  new NoteContent.MultipleChoice.Option("B", "Python"),
                  new NoteContent.MultipleChoice.Option("C", "Ruby"),
                  new NoteContent.MultipleChoice.Option("D", "C#")),
              List.of("A"),
              "Java runs on the JVM.");
      Note note = Note.create(NoteId.generate(), deckId, content, null);

      noteRepository.save(note);
      em.flush();
      em.clear();
      Note loaded = findById(note.getId()).orElseThrow();

      assertThat(loaded.getNoteType()).isEqualTo(NoteType.MULTIPLE_CHOICE);
      NoteContent.MultipleChoice loadedContent = (NoteContent.MultipleChoice) loaded.getContent();
      assertThat(loadedContent.question()).isEqualTo("Which is a JVM language?");
      assertThat(loadedContent.options()).hasSize(4);
      assertThat(loadedContent.correctOptionKeys()).containsExactly("A");
      assertThat(loadedContent.explanation()).isEqualTo("Java runs on the JVM.");
    }

    @Test
    @DisplayName("FREE_TEXT note content round-trips correctly")
    void freeTextNoteRoundTrip() {
      NoteContent.FreeText content =
          new NoteContent.FreeText(
              "Explain hexagonal architecture.",
              "Ports and adapters pattern keeping domain independent.",
              "Award 1 point for mentioning ports, 1 for adapters.");
      Note note = Note.create(NoteId.generate(), deckId, content, null);

      noteRepository.save(note);
      em.flush();
      em.clear();
      Note loaded = findById(note.getId()).orElseThrow();

      assertThat(loaded.getNoteType()).isEqualTo(NoteType.FREE_TEXT);
      NoteContent.FreeText loadedContent = (NoteContent.FreeText) loaded.getContent();
      assertThat(loadedContent.prompt()).isEqualTo("Explain hexagonal architecture.");
      assertThat(loadedContent.expectedAnswer())
          .isEqualTo("Ports and adapters pattern keeping domain independent.");
      assertThat(loadedContent.gradingGuidance())
          .isEqualTo("Award 1 point for mentioning ports, 1 for adapters.");
    }
  }

  @Nested
  @DisplayName("findAll with filters")
  class FindAllTests {

    @Test
    @DisplayName("filters by deckId")
    void filtersByDeckId() {
      Note note = Note.create(NoteId.generate(), deckId, new NoteContent.Basic("Q", "A"), null);
      noteRepository.save(note);
      em.flush();
      em.clear();

      List<Note> result = noteRepository.findAll(alice, deckId, null, null, null, 0, 10);
      assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("filters by noteType")
    void filtersByNoteType() {
      noteRepository.save(
          Note.create(NoteId.generate(), deckId, new NoteContent.Basic("Q1", "A1"), null));
      noteRepository.save(
          Note.create(NoteId.generate(), deckId, new NoteContent.Reversed("Q2", "A2"), null));
      em.flush();
      em.clear();

      List<Note> result = noteRepository.findAll(alice, deckId, NoteType.BASIC, null, null, 0, 10);
      assertThat(result).hasSize(1);
      assertThat(result.getFirst().getNoteType()).isEqualTo(NoteType.BASIC);
    }

    @Test
    @DisplayName("filters by tag")
    void filtersByTag() {
      noteRepository.save(
          Note.create(
              NoteId.generate(), deckId, new NoteContent.Basic("Q1", "A1"), List.of("java")));
      noteRepository.save(
          Note.create(
              NoteId.generate(), deckId, new NoteContent.Basic("Q2", "A2"), List.of("python")));
      em.flush();
      em.clear();

      List<Note> result = noteRepository.findAll(alice, deckId, null, "java", null, 0, 10);
      assertThat(result).hasSize(1);
      assertThat(result.getFirst().getTags()).contains("java");
    }

    @Test
    @DisplayName("pagination works correctly")
    void pagination() {
      for (int i = 0; i < 5; i++) {
        noteRepository.save(
            Note.create(NoteId.generate(), deckId, new NoteContent.Basic("Q" + i, "A" + i), null));
      }
      em.flush();
      em.clear();

      List<Note> page0 = noteRepository.findAll(alice, deckId, null, null, null, 0, 2);
      List<Note> page1 = noteRepository.findAll(alice, deckId, null, null, null, 2, 2);

      assertThat(page0).hasSize(2);
      assertThat(page1).hasSize(2);
    }

    @Test
    @DisplayName("countAll returns correct total")
    void countAll() {
      for (int i = 0; i < 3; i++) {
        noteRepository.save(
            Note.create(NoteId.generate(), deckId, new NoteContent.Basic("Q" + i, "A" + i), null));
      }
      em.flush();
      em.clear();

      long count = noteRepository.countAll(alice, deckId, null, null, null);
      assertThat(count).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("version increment on update")
  class VersionTests {

    @Test
    @DisplayName("version increments after updateContent")
    void versionIncrementsAfterUpdate() {
      Note note = Note.create(NoteId.generate(), deckId, new NoteContent.Basic("Q", "A"), null);
      noteRepository.save(note);
      em.flush();
      em.clear();

      assertThat(findById(note.getId()).orElseThrow().getVersion()).isEqualTo(1);

      note.updateContent(new NoteContent.Basic("Q updated", "A updated"));
      noteRepository.save(note);
      em.flush();
      em.clear();

      assertThat(findById(note.getId()).orElseThrow().getVersion()).isEqualTo(2);
    }
  }

  @Test
  @DisplayName("deleteById removes note")
  void deletesNote() {
    Note note = Note.create(NoteId.generate(), deckId, new NoteContent.Basic("Q", "A"), null);
    noteRepository.save(note);
    em.flush();
    em.clear();

    noteRepository.deleteById(note.getId());
    em.flush();
    em.clear();

    assertThat(findById(note.getId())).isEmpty();
  }

  // ---------------------------------------------------------------
  // SECURITY: IDOR — cross-tenant isolation (Issue 1)
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("IDOR: owner-scoped note listing")
  class OwnerScopedListingTests {

    private OwnerId bob;
    private DeckId bobDeckId;

    @BeforeEach
    void seedBob() {
      bob = OwnerId.generate();
      UUID bobUuid = bob.value();
      em.createNativeQuery(
              "INSERT INTO user_account(id, email, display_name) VALUES (:id, :email, :name)"
                  + " ON CONFLICT DO NOTHING")
          .setParameter("id", bobUuid)
          .setParameter("email", bobUuid + "@test.com")
          .setParameter("name", "Bob")
          .executeUpdate();

      bobDeckId = DeckId.generate();
      deckRepository.save(Deck.create(bobDeckId, bob, "Bob Deck", null));
      em.flush();
      em.clear();
    }

    @Test
    @DisplayName("findAll(ownerId=alice, deckId=null) must NOT return Bob's notes")
    void findAllWithNullDeckIdReturnsOnlyOwnersNotes() {
      // Seed one note for Alice
      Note aliceNote =
          Note.create(NoteId.generate(), deckId, new NoteContent.Basic("Alice Q", "Alice A"), null);
      noteRepository.save(aliceNote);

      // Seed one note for Bob
      Note bobNote =
          Note.create(NoteId.generate(), bobDeckId, new NoteContent.Basic("Bob Q", "Bob A"), null);
      noteRepository.save(bobNote);
      em.flush();
      em.clear();

      // Alice lists her notes without specifying a deckId — must see only her own
      List<Note> result = noteRepository.findAll(alice, null, null, null, null, 0, 10);

      assertThat(result).hasSize(1);
      assertThat(result.getFirst().getDeckId()).isEqualTo(deckId);
    }

    @Test
    @DisplayName("countAll(ownerId=alice, deckId=null) must NOT count Bob's notes")
    void countAllWithNullDeckIdCountsOnlyOwnersNotes() {
      noteRepository.save(
          Note.create(NoteId.generate(), deckId, new NoteContent.Basic("A Q", "A A"), null));
      noteRepository.save(
          Note.create(NoteId.generate(), bobDeckId, new NoteContent.Basic("B Q", "B A"), null));
      em.flush();
      em.clear();

      long count = noteRepository.countAll(alice, null, null, null, null);

      assertThat(count).isEqualTo(1);
    }
  }
}
