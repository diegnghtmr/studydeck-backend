package com.studydeck.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studydeck.application.common.Page;
import com.studydeck.application.common.PageRequest;
import com.studydeck.application.exception.NotFoundException;
import com.studydeck.application.support.FixedClockPort;
import com.studydeck.application.support.InMemoryAuditEventPort;
import com.studydeck.application.support.InMemoryCardRepository;
import com.studydeck.application.support.InMemoryCardScheduleStateRepository;
import com.studydeck.application.support.InMemoryDeckRepository;
import com.studydeck.application.support.InMemoryNoteRepository;
import com.studydeck.application.support.SequentialIdGenerator;
import com.studydeck.domain.model.Card;
import com.studydeck.domain.model.Deck;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.NoteContent;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.CreateNoteUseCase;
import com.studydeck.domain.port.in.DeleteCardUseCase;
import com.studydeck.domain.port.in.GetCardQuery;
import com.studydeck.domain.port.in.ListCardsQuery;
import com.studydeck.domain.port.in.UpdateCardUseCase;
import com.studydeck.domain.service.CardGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Use-case tests for Card services — plain Java, no Spring. */
class CardServiceTest {

  private InMemoryDeckRepository deckRepo;
  private InMemoryNoteRepository noteRepo;
  private InMemoryCardRepository cardRepo;
  private InMemoryAuditEventPort auditPort;
  private SequentialIdGenerator idGen;

  private CreateNoteUseCase createNote;
  private ListCardsQuery listCards;
  private GetCardQuery getCard;
  private UpdateCardUseCase updateCard;
  private DeleteCardUseCase deleteCard;

  private final OwnerId alice = OwnerId.generate();
  private final OwnerId bob = OwnerId.generate();
  private DeckId aliceDeck;
  private DeckId bobDeck;

  @BeforeEach
  void setUp() {
    deckRepo = new InMemoryDeckRepository();
    noteRepo = new InMemoryNoteRepository();
    cardRepo = new InMemoryCardRepository();
    auditPort = new InMemoryAuditEventPort();
    idGen = new SequentialIdGenerator();
    CardGenerator cardGenerator = new CardGenerator();

    aliceDeck = DeckId.generate();
    deckRepo.save(Deck.create(aliceDeck, alice, "Alice Deck", null));

    bobDeck = DeckId.generate();
    deckRepo.save(Deck.create(bobDeck, bob, "Bob Deck", null));

    // Wire note → deck resolver for card repo deck-level filtering
    cardRepo.setNoteIdToDeckId(
        noteId -> noteRepo.findById(noteId).map(n -> n.getDeckId()).orElse(null));

    InMemoryCardScheduleStateRepository scheduleRepo = new InMemoryCardScheduleStateRepository();
    FixedClockPort clock = FixedClockPort.epoch();
    NoteService noteService =
        new NoteService(
            deckRepo, noteRepo, cardRepo, scheduleRepo, clock, auditPort, idGen, cardGenerator);
    createNote = noteService;

    CardService cardService = new CardService(deckRepo, noteRepo, cardRepo, auditPort);
    listCards = cardService;
    getCard = cardService;
    updateCard = cardService;
    deleteCard = cardService;
  }

  // ---------------------------------------------------------------
  // LIST CARDS
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("ListCards")
  class ListCardsTests {

    @Test
    @DisplayName("returns only cards from decks owned by the caller")
    void returnsOnlyCallerCards() {
      // Alice creates 2 notes (BASIC = 1 card each)
      createNote.execute(
          new CreateNoteUseCase.Command(alice, aliceDeck, new NoteContent.Basic("Q1", "A1"), null));
      createNote.execute(
          new CreateNoteUseCase.Command(alice, aliceDeck, new NoteContent.Basic("Q2", "A2"), null));
      // Bob creates 1 note
      createNote.execute(
          new CreateNoteUseCase.Command(bob, bobDeck, new NoteContent.Basic("BQ", "BA"), null));

      Page<Card> page =
          listCards.execute(new ListCardsQuery.Query(alice, null, null, PageRequest.defaultPage()));

      assertThat(page.content()).hasSize(2);
      assertThat(page.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("filters by suspended=true")
    void filtersBySuspended() {
      // Create 2 cards, suspend 1
      CreateNoteUseCase.Result r1 =
          createNote.execute(
              new CreateNoteUseCase.Command(
                  alice, aliceDeck, new NoteContent.Basic("Q1", "A1"), null));
      createNote.execute(
          new CreateNoteUseCase.Command(alice, aliceDeck, new NoteContent.Basic("Q2", "A2"), null));

      // Suspend the card from r1
      Card cardToSuspend = cardRepo.findByNoteId(r1.noteId()).getFirst();
      updateCard.execute(new UpdateCardUseCase.Command(alice, cardToSuspend.getId(), true));

      Page<Card> suspendedPage =
          listCards.execute(new ListCardsQuery.Query(alice, null, true, PageRequest.defaultPage()));
      Page<Card> activePage =
          listCards.execute(
              new ListCardsQuery.Query(alice, null, false, PageRequest.defaultPage()));

      assertThat(suspendedPage.content()).hasSize(1);
      assertThat(activePage.content()).hasSize(1);
    }
  }

  // ---------------------------------------------------------------
  // GET CARD
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("GetCard")
  class GetCardTests {

    @Test
    @DisplayName("returns card owned by caller")
    void returnsOwnCard() {
      CreateNoteUseCase.Result result =
          createNote.execute(
              new CreateNoteUseCase.Command(
                  alice, aliceDeck, new NoteContent.Basic("Q", "A"), null));
      var cardId = result.cardIds().getFirst();

      Card card = getCard.execute(new GetCardQuery.Query(alice, cardId));

      assertThat(card.getId()).isEqualTo(cardId);
    }

    @Test
    @DisplayName("throws NotFoundException for card in another user's deck")
    void throwsForOtherUserCard() {
      CreateNoteUseCase.Result bobResult =
          createNote.execute(
              new CreateNoteUseCase.Command(bob, bobDeck, new NoteContent.Basic("Q", "A"), null));
      var bobCardId = bobResult.cardIds().getFirst();

      assertThatThrownBy(() -> getCard.execute(new GetCardQuery.Query(alice, bobCardId)))
          .isInstanceOf(NotFoundException.class);
    }
  }

  // ---------------------------------------------------------------
  // UPDATE CARD
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("UpdateCard — suspend/unsuspend")
  class UpdateCardTests {

    @Test
    @DisplayName("suspends a card")
    void suspendsCard() {
      CreateNoteUseCase.Result result =
          createNote.execute(
              new CreateNoteUseCase.Command(
                  alice, aliceDeck, new NoteContent.Basic("Q", "A"), null));
      var cardId = result.cardIds().getFirst();

      updateCard.execute(new UpdateCardUseCase.Command(alice, cardId, true));

      assertThat(cardRepo.findById(cardId).orElseThrow().isSuspended()).isTrue();
    }

    @Test
    @DisplayName("unsuspends a card")
    void unsuspendsCard() {
      CreateNoteUseCase.Result result =
          createNote.execute(
              new CreateNoteUseCase.Command(
                  alice, aliceDeck, new NoteContent.Basic("Q", "A"), null));
      var cardId = result.cardIds().getFirst();
      updateCard.execute(new UpdateCardUseCase.Command(alice, cardId, true)); // suspend first

      updateCard.execute(new UpdateCardUseCase.Command(alice, cardId, false)); // then unsuspend

      assertThat(cardRepo.findById(cardId).orElseThrow().isSuspended()).isFalse();
    }

    @Test
    @DisplayName("emits audit event card.updated")
    void emitsAuditEvent() {
      CreateNoteUseCase.Result result =
          createNote.execute(
              new CreateNoteUseCase.Command(
                  alice, aliceDeck, new NoteContent.Basic("Q", "A"), null));
      var cardId = result.cardIds().getFirst();
      auditPort.clear();

      updateCard.execute(new UpdateCardUseCase.Command(alice, cardId, true));

      assertThat(auditPort.hasAction("card.updated")).isTrue();
    }

    @Test
    @DisplayName("throws NotFoundException for card in another user's deck")
    void throwsForOtherUserCard() {
      CreateNoteUseCase.Result bobResult =
          createNote.execute(
              new CreateNoteUseCase.Command(bob, bobDeck, new NoteContent.Basic("Q", "A"), null));
      var bobCardId = bobResult.cardIds().getFirst();

      assertThatThrownBy(
              () -> updateCard.execute(new UpdateCardUseCase.Command(alice, bobCardId, true)))
          .isInstanceOf(NotFoundException.class);
    }
  }

  // ---------------------------------------------------------------
  // DELETE CARD
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("DeleteCard")
  class DeleteCardTests {

    @Test
    @DisplayName("deletes a card")
    void deletesCard() {
      CreateNoteUseCase.Result result =
          createNote.execute(
              new CreateNoteUseCase.Command(
                  alice, aliceDeck, new NoteContent.Basic("Q", "A"), null));
      var cardId = result.cardIds().getFirst();

      deleteCard.execute(new DeleteCardUseCase.Command(alice, cardId));

      assertThat(cardRepo.findById(cardId)).isEmpty();
    }

    @Test
    @DisplayName("emits audit event card.deleted")
    void emitsAuditEvent() {
      CreateNoteUseCase.Result result =
          createNote.execute(
              new CreateNoteUseCase.Command(
                  alice, aliceDeck, new NoteContent.Basic("Q", "A"), null));
      var cardId = result.cardIds().getFirst();
      auditPort.clear();

      deleteCard.execute(new DeleteCardUseCase.Command(alice, cardId));

      assertThat(auditPort.hasAction("card.deleted")).isTrue();
    }

    @Test
    @DisplayName("throws NotFoundException for card in another user's deck")
    void throwsForOtherUserCard() {
      CreateNoteUseCase.Result bobResult =
          createNote.execute(
              new CreateNoteUseCase.Command(bob, bobDeck, new NoteContent.Basic("Q", "A"), null));
      var bobCardId = bobResult.cardIds().getFirst();

      assertThatThrownBy(() -> deleteCard.execute(new DeleteCardUseCase.Command(alice, bobCardId)))
          .isInstanceOf(NotFoundException.class);
    }
  }
}
