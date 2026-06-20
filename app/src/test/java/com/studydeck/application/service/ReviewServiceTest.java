package com.studydeck.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studydeck.application.exception.NotFoundException;
import com.studydeck.application.support.FixedClockPort;
import com.studydeck.application.support.InMemoryAuditEventPort;
import com.studydeck.application.support.InMemoryCardRepository;
import com.studydeck.application.support.InMemoryCardScheduleStateRepository;
import com.studydeck.application.support.InMemoryDeckRepository;
import com.studydeck.application.support.InMemoryNoteRepository;
import com.studydeck.application.support.InMemoryReviewLogRepository;
import com.studydeck.application.support.InMemoryReviewSessionRepository;
import com.studydeck.domain.model.Card;
import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.CardPayload;
import com.studydeck.domain.model.CardScheduleState;
import com.studydeck.domain.model.CardState;
import com.studydeck.domain.model.Deck;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.Note;
import com.studydeck.domain.model.NoteContent;
import com.studydeck.domain.model.NoteId;
import com.studydeck.domain.model.NoteType;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.ReviewLog;
import com.studydeck.domain.model.ReviewRating;
import com.studydeck.domain.port.in.GetDeckStatsQuery;
import com.studydeck.domain.port.in.ListDueCardsQuery;
import com.studydeck.domain.port.in.StartReviewSessionUseCase;
import com.studydeck.domain.port.in.SubmitReviewUseCase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Application-layer unit tests for {@link ReviewService}.
 *
 * <p>Pure Java — no Spring context. Uses in-memory stubs for all output ports.
 */
class ReviewServiceTest {

  private InMemoryDeckRepository deckRepo;
  private InMemoryNoteRepository noteRepo;
  private InMemoryCardRepository cardRepo;
  private InMemoryCardScheduleStateRepository scheduleRepo;
  private InMemoryReviewLogRepository logRepo;
  private InMemoryReviewSessionRepository sessionRepo;
  private InMemoryAuditEventPort auditPort;
  private FixedClockPort clock;

  private ReviewService sut;

  private final OwnerId alice = OwnerId.generate();
  private DeckId aliceDeck;
  private NoteId aliceNote;
  private CardId aliceCard;

  @BeforeEach
  void setUp() {
    deckRepo = new InMemoryDeckRepository();
    noteRepo = new InMemoryNoteRepository();
    cardRepo = new InMemoryCardRepository();
    scheduleRepo = new InMemoryCardScheduleStateRepository();
    logRepo = new InMemoryReviewLogRepository();
    sessionRepo = new InMemoryReviewSessionRepository();
    auditPort = new InMemoryAuditEventPort();
    clock = FixedClockPort.epoch();

    sut =
        new ReviewService(
            deckRepo, noteRepo, cardRepo, scheduleRepo, logRepo, sessionRepo, auditPort, clock);

    // Seed deck + note + card
    aliceDeck = DeckId.generate();
    deckRepo.save(Deck.create(aliceDeck, alice, "Alice Deck", null));

    aliceNote = NoteId.generate();
    noteRepo.save(Note.create(aliceNote, aliceDeck, new NoteContent.Basic("Q", "A"), null));

    aliceCard = CardId.generate();
    cardRepo.save(
        Card.create(
            aliceCard,
            aliceNote,
            NoteType.BASIC,
            "forward",
            0,
            new CardPayload.BasicPrompt("Q"),
            new CardPayload.BasicAnswer("A")));

    // Wire note→deck resolver so deck-level card count works in stats
    cardRepo.setNoteIdToDeckId(
        noteId -> noteRepo.findById(noteId).map(n -> n.getDeckId()).orElse(null));
  }

  // ---------------------------------------------------------------
  // SubmitReview — core scheduling logic
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("SubmitReview")
  class SubmitReviewTests {

    @Test
    @DisplayName("GOOD review advances state from NEW and returns scheduledDays > 0")
    void goodReviewFromNew_schedulesForFuture() {
      // Arrange: card has a NEW schedule state
      scheduleRepo.save(alice, aliceCard, CardScheduleState.newFsrsCard(clock.now()));

      // Act
      SubmitReviewUseCase.Command command =
          new SubmitReviewUseCase.Command(alice, aliceCard, ReviewRating.GOOD, null, null);
      SubmitReviewUseCase.Result result = sut.execute(command);

      // Assert: nextState has scheduledDays > 0 (graduated)
      assertThat(result.reviewResult().nextState().scheduledDays()).isGreaterThan(0);
      assertThat(result.reviewResult().nextState().dueAt()).isAfter(clock.now());
      assertThat(result.reviewResult().previousState().state()).isEqualTo(CardState.NEW);
      assertThat(result.historyEntryId()).isNotNull();
    }

    @Test
    @DisplayName("AGAIN review from NEW keeps card in learning")
    void againFromNew_keepsLearning() {
      scheduleRepo.save(alice, aliceCard, CardScheduleState.newFsrsCard(clock.now()));

      SubmitReviewUseCase.Command command =
          new SubmitReviewUseCase.Command(alice, aliceCard, ReviewRating.AGAIN, null, null);
      SubmitReviewUseCase.Result result = sut.execute(command);

      // AGAIN from NEW: card stays in LEARNING (short step)
      assertThat(result.reviewResult().nextState().state())
          .isIn(CardState.LEARNING, CardState.RELEARNING);
    }

    @Test
    @DisplayName("SubmitReview appends exactly one ReviewLog entry")
    void submitAppendsSingleLog() {
      scheduleRepo.save(alice, aliceCard, CardScheduleState.newFsrsCard(clock.now()));

      sut.execute(new SubmitReviewUseCase.Command(alice, aliceCard, ReviewRating.GOOD, null, null));

      assertThat(logRepo.size()).isEqualTo(1);
      ReviewLog log = logRepo.all().get(0);
      assertThat(log.cardId()).isEqualTo(aliceCard);
      assertThat(log.rating()).isEqualTo(ReviewRating.GOOD);
    }

    @Test
    @DisplayName("SubmitReview stores responseTimeMs in log when provided")
    void submitStoresResponseTime() {
      scheduleRepo.save(alice, aliceCard, CardScheduleState.newFsrsCard(clock.now()));

      sut.execute(new SubmitReviewUseCase.Command(alice, aliceCard, ReviewRating.EASY, null, 1500));

      ReviewLog log = logRepo.all().get(0);
      assertThat(log.responseTimeMs()).isEqualTo(1500);
    }

    @Test
    @DisplayName("SubmitReview updates CardScheduleState in repository")
    void submitUpdatesScheduleState() {
      scheduleRepo.save(alice, aliceCard, CardScheduleState.newFsrsCard(clock.now()));

      sut.execute(new SubmitReviewUseCase.Command(alice, aliceCard, ReviewRating.GOOD, null, null));

      CardScheduleState updated = scheduleRepo.findByCardId(aliceCard).orElseThrow();
      assertThat(updated.state()).isNotEqualTo(CardState.NEW);
    }

    @Test
    @DisplayName("SubmitReview enforces ownership — rejects card owned by another user")
    void submitEnforcesOwnership() {
      OwnerId bob = OwnerId.generate();
      scheduleRepo.save(alice, aliceCard, CardScheduleState.newFsrsCard(clock.now()));

      SubmitReviewUseCase.Command command =
          new SubmitReviewUseCase.Command(bob, aliceCard, ReviewRating.GOOD, null, null);

      assertThatThrownBy(() -> sut.execute(command)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("SubmitReview defaults to FSRS NEW state when no schedule exists")
    void submitDefaultsToNewWhenNoSchedule() {
      // No scheduleRepo entry — should be treated as NEW
      SubmitReviewUseCase.Command command =
          new SubmitReviewUseCase.Command(alice, aliceCard, ReviewRating.GOOD, null, null);
      SubmitReviewUseCase.Result result = sut.execute(command);

      assertThat(result.reviewResult().previousState().state()).isEqualTo(CardState.NEW);
    }
  }

  // ---------------------------------------------------------------
  // ListDueCards — filtering by dueAt
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("ListDueCards")
  class ListDueCardsTests {

    @Test
    @DisplayName("Due card appears in due list")
    void dueCardAppearsInList() {
      // Card due at epoch = now → should appear
      scheduleRepo.save(alice, aliceCard, CardScheduleState.newFsrsCard(clock.now()));

      List<Card> due = sut.execute(new ListDueCardsQuery.Query(alice, aliceDeck, 20));

      assertThat(due).hasSize(1);
      assertThat(due.get(0).getId()).isEqualTo(aliceCard);
    }

    @Test
    @DisplayName("Card not yet due does not appear in due list")
    void notDueCardNotInList() {
      // Card due far in the future
      Instant future = clock.now().plusSeconds(86400 * 10);
      scheduleRepo.save(
          alice,
          aliceCard,
          new CardScheduleState(
              com.studydeck.domain.model.SchedulerAlgorithm.FSRS,
              CardState.REVIEW,
              5.0,
              5.0,
              0.9,
              1,
              0,
              10,
              future,
              clock.now()));

      List<Card> due = sut.execute(new ListDueCardsQuery.Query(alice, aliceDeck, 20));

      assertThat(due).isEmpty();
    }
  }

  // ---------------------------------------------------------------
  // DeckStats — aggregates
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("GetDeckStats")
  class DeckStatsTests {

    @Test
    @DisplayName("Stats reflect total cards and due cards")
    void statsReflectCardCounts() {
      scheduleRepo.save(alice, aliceCard, CardScheduleState.newFsrsCard(clock.now()));

      GetDeckStatsQuery.DeckStatsResult stats =
          sut.execute(new GetDeckStatsQuery.Query(alice, aliceDeck));

      assertThat(stats.totalCards()).isEqualTo(1);
      assertThat(stats.dueToday()).isEqualTo(1);
      assertThat(stats.deckId()).isEqualTo(aliceDeck);
    }

    @Test
    @DisplayName("Stats enforce ownership — rejects deck owned by another user")
    void statsEnforcesOwnership() {
      OwnerId bob = OwnerId.generate();
      assertThatThrownBy(() -> sut.execute(new GetDeckStatsQuery.Query(bob, aliceDeck)))
          .isInstanceOf(NotFoundException.class);
    }
  }

  // ---------------------------------------------------------------
  // StartReviewSession
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("StartReviewSession")
  class StartReviewSessionTests {

    @Test
    @DisplayName("Creates session and returns UUID")
    void createsSession() {
      UUID sessionId = sut.execute(new StartReviewSessionUseCase.Command(alice, aliceDeck, 20));

      assertThat(sessionId).isNotNull();
      assertThat(sessionRepo.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("maxCards out of range throws IllegalArgumentException")
    void invalidMaxCardsThrows() {
      assertThatThrownBy(
              () -> sut.execute(new StartReviewSessionUseCase.Command(alice, aliceDeck, 0)))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
