package com.studydeck.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studydeck.application.common.Page;
import com.studydeck.application.common.PageRequest;
import com.studydeck.application.exception.NotFoundException;
import com.studydeck.application.support.FixedClockPort;
import com.studydeck.application.support.InMemoryAuditEventPort;
import com.studydeck.application.support.InMemoryCardRepository;
import com.studydeck.application.support.InMemoryDeckRepository;
import com.studydeck.application.support.InMemoryNoteRepository;
import com.studydeck.application.support.SequentialIdGenerator;
import com.studydeck.domain.model.Deck;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.Note;
import com.studydeck.domain.model.NoteContent;
import com.studydeck.domain.model.NoteId;
import com.studydeck.domain.model.NoteType;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.CreateNoteUseCase;
import com.studydeck.domain.port.in.DeleteNoteUseCase;
import com.studydeck.domain.port.in.GetNoteQuery;
import com.studydeck.domain.port.in.ListCardsForNoteQuery;
import com.studydeck.domain.port.in.ListNotesQuery;
import com.studydeck.domain.port.in.UpdateNoteUseCase;
import com.studydeck.domain.service.CardGenerator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Use-case tests for Note services — plain Java, no Spring. */
class NoteServiceTest {

  private InMemoryDeckRepository deckRepo;
  private InMemoryNoteRepository noteRepo;
  private InMemoryCardRepository cardRepo;
  private InMemoryAuditEventPort auditPort;
  private SequentialIdGenerator idGen;

  private CreateNoteUseCase createNote;
  private ListNotesQuery listNotes;
  private GetNoteQuery getNote;
  private UpdateNoteUseCase updateNote;
  private DeleteNoteUseCase deleteNote;
  private ListCardsForNoteQuery listCardsForNote;

  private final OwnerId alice = OwnerId.generate();
  private final OwnerId bob = OwnerId.generate();

  /** A deck owned by Alice — pre-seeded for most tests. */
  private DeckId aliceDeck;

  @BeforeEach
  void setUp() {
    deckRepo = new InMemoryDeckRepository();
    noteRepo = new InMemoryNoteRepository();
    cardRepo = new InMemoryCardRepository();
    auditPort = new InMemoryAuditEventPort();
    idGen = new SequentialIdGenerator();
    FixedClockPort clock = FixedClockPort.epoch();
    CardGenerator cardGenerator = new CardGenerator();

    // Seed a deck owned by Alice
    aliceDeck = DeckId.generate();
    deckRepo.save(Deck.create(aliceDeck, alice, "Alice Deck", null));

    NoteService sut =
        new NoteService(deckRepo, noteRepo, cardRepo, auditPort, idGen, cardGenerator);
    createNote = sut;
    listNotes = sut;
    getNote = sut;
    updateNote = sut;
    deleteNote = sut;
    listCardsForNote = sut;
  }

  // ---------------------------------------------------------------
  // CREATE NOTE — card generation per type
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("CreateNote — card generation")
  class CreateNoteCardGenerationTests {

    @Test
    @DisplayName("BASIC note generates exactly 1 card")
    void basicNoteGeneratesOneCard() {
      NoteContent content = new NoteContent.Basic("What is JVM?", "Java Virtual Machine");
      CreateNoteUseCase.Result result =
          createNote.execute(new CreateNoteUseCase.Command(alice, aliceDeck, content, null));

      assertThat(result.cardIds()).hasSize(1);
      assertThat(cardRepo.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("REVERSED note generates exactly 2 cards")
    void reversedNoteGeneratesTwoCards() {
      NoteContent content = new NoteContent.Reversed("Front", "Back");
      CreateNoteUseCase.Result result =
          createNote.execute(new CreateNoteUseCase.Command(alice, aliceDeck, content, null));

      assertThat(result.cardIds()).hasSize(2);
      assertThat(cardRepo.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("CLOZE note with 2 distinct deletion numbers generates 2 cards")
    void clozeNoteGeneratesCardPerDeletion() {
      NoteContent content = new NoteContent.Cloze("{{c1::Java}} runs on {{c2::JVM}}");
      CreateNoteUseCase.Result result =
          createNote.execute(new CreateNoteUseCase.Command(alice, aliceDeck, content, null));

      assertThat(result.cardIds()).hasSize(2);
      assertThat(cardRepo.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("CLOZE note with 3 distinct deletion numbers generates 3 cards")
    void clozeNoteGeneratesThreeCards() {
      NoteContent content = new NoteContent.Cloze("{{c1::A}} + {{c2::B}} = {{c3::C}}");
      CreateNoteUseCase.Result result =
          createNote.execute(new CreateNoteUseCase.Command(alice, aliceDeck, content, null));

      assertThat(result.cardIds()).hasSize(3);
    }

    @Test
    @DisplayName("MULTIPLE_CHOICE note generates exactly 1 card")
    void mcqNoteGeneratesOneCard() {
      NoteContent content =
          new NoteContent.MultipleChoice(
              "What is 2+2?",
              List.of(
                  new NoteContent.MultipleChoice.Option("A", "3"),
                  new NoteContent.MultipleChoice.Option("B", "4"),
                  new NoteContent.MultipleChoice.Option("C", "5"),
                  new NoteContent.MultipleChoice.Option("D", "6")),
              List.of("B"),
              null);
      CreateNoteUseCase.Result result =
          createNote.execute(new CreateNoteUseCase.Command(alice, aliceDeck, content, null));

      assertThat(result.cardIds()).hasSize(1);
    }

    @Test
    @DisplayName("FREE_TEXT note generates exactly 1 card")
    void freeTextNoteGeneratesOneCard() {
      NoteContent content =
          new NoteContent.FreeText("Explain polymorphism", "Ability to take multiple forms", null);
      CreateNoteUseCase.Result result =
          createNote.execute(new CreateNoteUseCase.Command(alice, aliceDeck, content, null));

      assertThat(result.cardIds()).hasSize(1);
    }

    @Test
    @DisplayName("note and cards are persisted atomically")
    void noteAndCardsPersistedAtomically() {
      NoteContent content = new NoteContent.Basic("Q", "A");
      CreateNoteUseCase.Result result =
          createNote.execute(new CreateNoteUseCase.Command(alice, aliceDeck, content, null));

      assertThat(noteRepo.findById(result.noteId())).isPresent();
      assertThat(cardRepo.findByNoteId(result.noteId())).hasSize(1);
    }

    @Test
    @DisplayName("creates note with tags")
    void createsNoteWithTags() {
      NoteContent content = new NoteContent.Basic("Q", "A");
      CreateNoteUseCase.Result result =
          createNote.execute(
              new CreateNoteUseCase.Command(alice, aliceDeck, content, List.of("java", "oop")));

      Note note = noteRepo.findById(result.noteId()).orElseThrow();
      assertThat(note.getTags()).containsExactlyInAnyOrder("java", "oop");
    }
  }

  // ---------------------------------------------------------------
  // CREATE NOTE — audit
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("CreateNote — audit")
  class CreateNoteAuditTests {

    @Test
    @DisplayName("emits audit event note.created")
    void emitsAuditEvent() {
      NoteContent content = new NoteContent.Basic("Q", "A");
      createNote.execute(new CreateNoteUseCase.Command(alice, aliceDeck, content, null));

      assertThat(auditPort.hasAction("note.created")).isTrue();
      assertThat(auditPort.recorded().getFirst().actorId()).isEqualTo(alice);
    }
  }

  // ---------------------------------------------------------------
  // CREATE NOTE — ownership enforcement
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("CreateNote — ownership")
  class CreateNoteOwnershipTests {

    @Test
    @DisplayName("throws NotFoundException when deck does not exist")
    void throwsWhenDeckNotFound() {
      DeckId unknown = DeckId.generate();
      NoteContent content = new NoteContent.Basic("Q", "A");

      assertThatThrownBy(
              () ->
                  createNote.execute(new CreateNoteUseCase.Command(alice, unknown, content, null)))
          .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("throws NotFoundException when deck belongs to another user")
    void throwsWhenDeckBelongsToAnotherUser() {
      DeckId bobDeck = DeckId.generate();
      deckRepo.save(Deck.create(bobDeck, bob, "Bob Deck", null));
      NoteContent content = new NoteContent.Basic("Q", "A");

      assertThatThrownBy(
              () ->
                  createNote.execute(new CreateNoteUseCase.Command(alice, bobDeck, content, null)))
          .isInstanceOf(NotFoundException.class);
    }
  }

  // ---------------------------------------------------------------
  // LIST NOTES
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("ListNotes")
  class ListNotesTests {

    @Test
    @DisplayName("returns notes in the given deck")
    void returnsNotesInDeck() {
      NoteContent c = new NoteContent.Basic("Q", "A");
      createNote.execute(new CreateNoteUseCase.Command(alice, aliceDeck, c, null));
      createNote.execute(new CreateNoteUseCase.Command(alice, aliceDeck, c, null));

      Page<Note> page =
          listNotes.execute(
              new ListNotesQuery.Query(
                  alice, aliceDeck, null, null, null, PageRequest.defaultPage()));

      assertThat(page.content()).hasSize(2);
      assertThat(page.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("filters by note type")
    void filtersByNoteType() {
      createNote.execute(
          new CreateNoteUseCase.Command(alice, aliceDeck, new NoteContent.Basic("Q", "A"), null));
      createNote.execute(
          new CreateNoteUseCase.Command(
              alice, aliceDeck, new NoteContent.Reversed("F", "B"), null));

      Page<Note> page =
          listNotes.execute(
              new ListNotesQuery.Query(
                  alice, aliceDeck, NoteType.BASIC, null, null, PageRequest.defaultPage()));

      assertThat(page.content()).hasSize(1);
      assertThat(page.content().getFirst().getNoteType()).isEqualTo(NoteType.BASIC);
    }

    @Test
    @DisplayName("filters by tag")
    void filtersByTag() {
      createNote.execute(
          new CreateNoteUseCase.Command(
              alice, aliceDeck, new NoteContent.Basic("Q1", "A1"), List.of("java")));
      createNote.execute(
          new CreateNoteUseCase.Command(
              alice, aliceDeck, new NoteContent.Basic("Q2", "A2"), List.of("python")));

      Page<Note> page =
          listNotes.execute(
              new ListNotesQuery.Query(
                  alice, aliceDeck, null, "java", null, PageRequest.defaultPage()));

      assertThat(page.content()).hasSize(1);
    }
  }

  // ---------------------------------------------------------------
  // GET NOTE
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("GetNote")
  class GetNoteTests {

    @Test
    @DisplayName("returns existing note")
    void returnsExistingNote() {
      CreateNoteUseCase.Result result =
          createNote.execute(
              new CreateNoteUseCase.Command(
                  alice, aliceDeck, new NoteContent.Basic("Q", "A"), null));

      Note note = getNote.execute(new GetNoteQuery.Query(alice, result.noteId()));

      assertThat(note.getId()).isEqualTo(result.noteId());
    }

    @Test
    @DisplayName("throws NotFoundException for unknown note")
    void throwsForUnknownNote() {
      assertThatThrownBy(() -> getNote.execute(new GetNoteQuery.Query(alice, NoteId.generate())))
          .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("throws NotFoundException when note belongs to another user's deck")
    void throwsForOtherUserNote() {
      DeckId bobDeck = DeckId.generate();
      deckRepo.save(Deck.create(bobDeck, bob, "Bob Deck", null));
      CreateNoteUseCase.Result bobResult =
          createNote.execute(
              new CreateNoteUseCase.Command(bob, bobDeck, new NoteContent.Basic("Q", "A"), null));

      assertThatThrownBy(() -> getNote.execute(new GetNoteQuery.Query(alice, bobResult.noteId())))
          .isInstanceOf(NotFoundException.class);
    }
  }

  // ---------------------------------------------------------------
  // UPDATE NOTE
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("UpdateNote")
  class UpdateNoteTests {

    @Test
    @DisplayName("re-generates cards when content changes — BASIC to REVERSED")
    void regeneratesCardsOnContentChange() {
      // Create a BASIC note (1 card)
      CreateNoteUseCase.Result created =
          createNote.execute(
              new CreateNoteUseCase.Command(
                  alice, aliceDeck, new NoteContent.Basic("Q", "A"), null));
      assertThat(cardRepo.findByNoteId(created.noteId())).hasSize(1);

      // Update to REVERSED (should produce 2 cards, old 1 deleted)
      UpdateNoteUseCase.Result updated =
          updateNote.execute(
              new UpdateNoteUseCase.Command(
                  alice, created.noteId(), new NoteContent.Reversed("Q", "A"), null));

      assertThat(updated.cardIds()).hasSize(2);
      assertThat(cardRepo.findByNoteId(created.noteId())).hasSize(2);
    }

    @Test
    @DisplayName("bumps version number on content update")
    void bumpsVersionOnUpdate() {
      CreateNoteUseCase.Result created =
          createNote.execute(
              new CreateNoteUseCase.Command(
                  alice, aliceDeck, new NoteContent.Basic("Q", "A"), null));

      UpdateNoteUseCase.Result updated =
          updateNote.execute(
              new UpdateNoteUseCase.Command(
                  alice, created.noteId(), new NoteContent.Basic("Q updated", "A"), null));

      assertThat(updated.version()).isGreaterThan(1);
      Note note = noteRepo.findById(created.noteId()).orElseThrow();
      assertThat(note.getVersion()).isGreaterThan(1);
    }

    @Test
    @DisplayName("emits audit event note.updated")
    void emitsAuditEvent() {
      CreateNoteUseCase.Result created =
          createNote.execute(
              new CreateNoteUseCase.Command(
                  alice, aliceDeck, new NoteContent.Basic("Q", "A"), null));
      auditPort.clear();

      updateNote.execute(
          new UpdateNoteUseCase.Command(
              alice, created.noteId(), new NoteContent.Basic("Q2", "A2"), null));

      assertThat(auditPort.hasAction("note.updated")).isTrue();
    }

    @Test
    @DisplayName("throws NotFoundException when updating note in another user's deck")
    void throwsForOtherUserNote() {
      DeckId bobDeck = DeckId.generate();
      deckRepo.save(Deck.create(bobDeck, bob, "Bob Deck", null));
      CreateNoteUseCase.Result bobResult =
          createNote.execute(
              new CreateNoteUseCase.Command(bob, bobDeck, new NoteContent.Basic("Q", "A"), null));

      assertThatThrownBy(
              () ->
                  updateNote.execute(
                      new UpdateNoteUseCase.Command(
                          alice, bobResult.noteId(), new NoteContent.Basic("Q2", "A2"), null)))
          .isInstanceOf(NotFoundException.class);
    }
  }

  // ---------------------------------------------------------------
  // DELETE NOTE
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("DeleteNote")
  class DeleteNoteTests {

    @Test
    @DisplayName("deletes note and all its cards")
    void deletesNoteAndCards() {
      CreateNoteUseCase.Result result =
          createNote.execute(
              new CreateNoteUseCase.Command(
                  alice, aliceDeck, new NoteContent.Reversed("F", "B"), null));

      deleteNote.execute(new DeleteNoteUseCase.Command(alice, result.noteId()));

      assertThat(noteRepo.findById(result.noteId())).isEmpty();
      assertThat(cardRepo.findByNoteId(result.noteId())).isEmpty();
    }

    @Test
    @DisplayName("emits audit event note.deleted")
    void emitsAuditEvent() {
      CreateNoteUseCase.Result result =
          createNote.execute(
              new CreateNoteUseCase.Command(
                  alice, aliceDeck, new NoteContent.Basic("Q", "A"), null));
      auditPort.clear();

      deleteNote.execute(new DeleteNoteUseCase.Command(alice, result.noteId()));

      assertThat(auditPort.hasAction("note.deleted")).isTrue();
    }
  }

  // ---------------------------------------------------------------
  // LIST CARDS FOR NOTE
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("ListCardsForNote")
  class ListCardsForNoteTests {

    @Test
    @DisplayName("returns cards ordered by ordinal")
    void returnsCardsOrderedByOrdinal() {
      CreateNoteUseCase.Result result =
          createNote.execute(
              new CreateNoteUseCase.Command(
                  alice, aliceDeck, new NoteContent.Reversed("Front", "Back"), null));

      var cards = listCardsForNote.execute(new ListCardsForNoteQuery.Query(alice, result.noteId()));

      assertThat(cards).hasSize(2);
      assertThat(cards.get(0).getOrdinal()).isLessThan(cards.get(1).getOrdinal());
    }

    @Test
    @DisplayName("throws NotFoundException for note in another user's deck")
    void throwsForOtherUserNote() {
      DeckId bobDeck = DeckId.generate();
      deckRepo.save(Deck.create(bobDeck, bob, "Bob Deck", null));
      CreateNoteUseCase.Result bobResult =
          createNote.execute(
              new CreateNoteUseCase.Command(bob, bobDeck, new NoteContent.Basic("Q", "A"), null));

      assertThatThrownBy(
              () ->
                  listCardsForNote.execute(
                      new ListCardsForNoteQuery.Query(alice, bobResult.noteId())))
          .isInstanceOf(NotFoundException.class);
    }
  }
}
