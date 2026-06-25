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
import com.studydeck.application.support.InMemoryUserAccountRepository;
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
import com.studydeck.domain.model.UserAccount;
import com.studydeck.domain.model.UserAccountStatus;
import com.studydeck.domain.port.in.GetDeckStatsQuery;
import com.studydeck.domain.port.in.GetNextCardQuery;
import com.studydeck.domain.port.in.ListDueCardsQuery;
import com.studydeck.domain.port.in.StartReviewSessionUseCase;
import com.studydeck.domain.port.in.SubmitReviewUseCase;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
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
  private InMemoryUserAccountRepository userAccountRepo;

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
    userAccountRepo = new InMemoryUserAccountRepository();

    sut =
        new ReviewService(
            deckRepo,
            noteRepo,
            cardRepo,
            scheduleRepo,
            logRepo,
            sessionRepo,
            auditPort,
            clock,
            userAccountRepo);

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

    @Test
    @DisplayName("reviewedToday uses supplied timezone, not UTC")
    void reviewedTodayUsesSuppliedTimezone() {
      // Clock is fixed at 2000-01-01T00:30:00Z (30 min after UTC midnight).
      // In UTC+14 (Line Islands), that instant is already 2000-01-01T14:30:00+14:00 — same day.
      // In UTC-5, that instant is 1999-12-31T19:30:00-05:00 — previous day.
      // A review logged at 1999-12-31T23:30:00Z (1 hour before the clock instant) falls:
      //   - In UTC+14: 2000-01-01T13:30+14 → same day as today → reviewedToday=1
      //   - In UTC-5:  1999-12-31T18:30-05 → previous day → reviewedToday=0
      //   - In UTC:    1999-12-31T23:30Z → previous day (UTC midnight boundary) → reviewedToday=0

      Instant clockInstant = Instant.parse("2000-01-01T00:30:00Z");
      clock.setFixedInstant(clockInstant);

      // Log a review at 1999-12-31T23:30:00Z (1 hour before clock)
      Instant reviewAt = Instant.parse("1999-12-31T23:30:00Z");
      scheduleRepo.save(alice, aliceCard, CardScheduleState.newFsrsCard(clockInstant));
      logRepo.save(
          alice,
          null,
          new com.studydeck.domain.model.ReviewLog(
              aliceCard,
              com.studydeck.domain.model.ReviewRating.GOOD,
              com.studydeck.domain.model.CardState.NEW,
              reviewAt,
              0,
              1,
              null));

      // UTC: reviewAt falls on 1999-12-31, clock on 2000-01-01 → different day → 0
      GetDeckStatsQuery.DeckStatsResult utcStats =
          sut.execute(new GetDeckStatsQuery.Query(alice, aliceDeck, ZoneId.of("UTC")));
      assertThat(utcStats.reviewedToday()).isEqualTo(0);

      // UTC-5: reviewAt is 1999-12-31T18:30-05, today is 1999-12-31T19:30-05
      // → same local day → reviewedToday should be 1
      GetDeckStatsQuery.DeckStatsResult minus5Stats =
          sut.execute(new GetDeckStatsQuery.Query(alice, aliceDeck, ZoneId.of("America/New_York")));
      // Note: America/New_York is UTC-5 in winter. Both reviewAt and clockInstant land on same day.
      assertThat(minus5Stats.reviewedToday()).isEqualTo(1);
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

  // ---------------------------------------------------------------
  // PresetResolution — desiredRetention wiring
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("PresetResolution — desiredRetention wiring")
  class PresetResolutionTests {

    @Test
    @DisplayName(
        "FSRS preset uses user's desiredRetention — different retention yields different schedule")
    void fsrsPresetUsesUserDesiredRetention() {
      OwnerId userA = OwnerId.generate();
      OwnerId userB = OwnerId.generate();

      UserAccount accountA =
          UserAccount.reconstitute(
              userA,
              "a@test.com",
              "A",
              UserAccountStatus.ACTIVE,
              40,
              0.85,
              10,
              "en",
              "UTC",
              com.studydeck.domain.model.SchedulerAlgorithm.FSRS,
              Instant.EPOCH,
              Instant.EPOCH);
      userAccountRepo.save(accountA);

      UserAccount accountB =
          UserAccount.reconstitute(
              userB,
              "b@test.com",
              "B",
              UserAccountStatus.ACTIVE,
              40,
              0.90,
              10,
              "en",
              "UTC",
              com.studydeck.domain.model.SchedulerAlgorithm.FSRS,
              Instant.EPOCH,
              Instant.EPOCH);
      userAccountRepo.save(accountB);

      DeckId deckA = DeckId.generate();
      deckRepo.save(Deck.create(deckA, userA, "Deck A", null));
      NoteId noteA = NoteId.generate();
      noteRepo.save(Note.create(noteA, deckA, new NoteContent.Basic("Q", "A"), null));
      CardId cardA = CardId.generate();
      cardRepo.save(
          Card.create(
              cardA,
              noteA,
              NoteType.BASIC,
              "forward",
              0,
              new CardPayload.BasicPrompt("Q"),
              new CardPayload.BasicAnswer("A")));
      scheduleRepo.save(userA, cardA, CardScheduleState.newFsrsCard(clock.now()));

      DeckId deckB = DeckId.generate();
      deckRepo.save(Deck.create(deckB, userB, "Deck B", null));
      NoteId noteB = NoteId.generate();
      noteRepo.save(Note.create(noteB, deckB, new NoteContent.Basic("Q", "A"), null));
      CardId cardB = CardId.generate();
      cardRepo.save(
          Card.create(
              cardB,
              noteB,
              NoteType.BASIC,
              "forward",
              0,
              new CardPayload.BasicPrompt("Q"),
              new CardPayload.BasicAnswer("A")));
      scheduleRepo.save(userB, cardB, CardScheduleState.newFsrsCard(clock.now()));

      // EASY on a NEW card goes directly to REVIEW via nextInterval(s0, desiredRetention),
      // so different desiredRetention values produce different scheduledDays.
      SubmitReviewUseCase.Result resultA =
          sut.execute(new SubmitReviewUseCase.Command(userA, cardA, ReviewRating.EASY, null, null));
      SubmitReviewUseCase.Result resultB =
          sut.execute(new SubmitReviewUseCase.Command(userB, cardB, ReviewRating.EASY, null, null));

      assertThat(resultA.reviewResult().nextState().scheduledDays())
          .isNotEqualTo(resultB.reviewResult().nextState().scheduledDays());
    }

    @Test
    @DisplayName("FSRS preset falls back to 0.90 when user not found in repository")
    void fsrsPresetFallsBackWhenUserAbsent() {
      scheduleRepo.save(alice, aliceCard, CardScheduleState.newFsrsCard(clock.now()));

      SubmitReviewUseCase.Command command =
          new SubmitReviewUseCase.Command(alice, aliceCard, ReviewRating.GOOD, null, null);

      SubmitReviewUseCase.Result result = sut.execute(command);
      assertThat(result.reviewResult().nextState().scheduledDays()).isGreaterThan(0);
    }
  }

  // ---------------------------------------------------------------
  // Algorithm Preference
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("AlgorithmPreference")
  class AlgorithmPreferenceTests {

    @Test
    @DisplayName("resolvePreset uses user SM2 algorithm when account has SM2 preference")
    void resolvePresetUsesSm2WhenUserPrefersSm2() {
      OwnerId user = OwnerId.generate();
      UserAccount account =
          UserAccount.reconstitute(
              user,
              "sm2user@test.com",
              "SM2 User",
              UserAccountStatus.ACTIVE,
              40,
              0.85,
              10,
              "en",
              "UTC",
              com.studydeck.domain.model.SchedulerAlgorithm.SM2,
              Instant.EPOCH,
              Instant.EPOCH);
      userAccountRepo.save(account);

      DeckId deck = DeckId.generate();
      deckRepo.save(Deck.create(deck, user, "Deck", null));
      NoteId note = NoteId.generate();
      noteRepo.save(Note.create(note, deck, new NoteContent.Basic("Q", "A"), null));
      CardId card = CardId.generate();
      cardRepo.save(
          Card.create(
              card,
              note,
              NoteType.BASIC,
              "forward",
              0,
              new CardPayload.BasicPrompt("Q"),
              new CardPayload.BasicAnswer("A")));
      scheduleRepo.save(user, card, CardScheduleState.newFsrsCard(clock.now()));

      SubmitReviewUseCase.Result result =
          sut.execute(new SubmitReviewUseCase.Command(user, card, ReviewRating.GOOD, null, null));

      assertThat(result.reviewResult().nextState().scheduledDays()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Partial update: schedulerAlgorithm persists to SM2")
    void partialUpdateSchedulerAlgorithmPersists() {
      OwnerId user = OwnerId.generate();
      UserAccount account =
          UserAccount.reconstitute(
              user,
              "pref@test.com",
              "User",
              UserAccountStatus.ACTIVE,
              40,
              0.90,
              10,
              "en",
              "UTC",
              com.studydeck.domain.model.SchedulerAlgorithm.FSRS,
              Instant.EPOCH,
              Instant.EPOCH);
      userAccountRepo.save(account);

      UserPreferencesService prefSvc = new UserPreferencesService(userAccountRepo, auditPort);

      prefSvc.execute(
          new com.studydeck.domain.port.in.UpdateUserPreferencesUseCase.Command(
              user,
              null,
              null,
              null,
              null,
              null,
              com.studydeck.domain.model.SchedulerAlgorithm.SM2));

      UserAccount updated = userAccountRepo.findById(user).orElseThrow();
      assertThat(updated.getSchedulerAlgorithm())
          .isEqualTo(com.studydeck.domain.model.SchedulerAlgorithm.SM2);
    }
  }

  // ---------------------------------------------------------------
  // New-cards-per-day cap
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("NewCardsPerDayCap")
  class NewCardsPerDayCapTests {

    private OwnerId capUser;
    private DeckId capDeck;

    @BeforeEach
    void setUpCapUser() {
      capUser = OwnerId.generate();
      capDeck = DeckId.generate();
      deckRepo.save(Deck.create(capDeck, capUser, "Cap Deck", null));
    }

    @Test
    @DisplayName("Cap hit: NEW card skipped, review card served instead")
    void capHit_newCardSkipped_reviewCardServed() {
      UserAccount account =
          UserAccount.reconstitute(
              capUser,
              "cap@test.com",
              "Cap",
              UserAccountStatus.ACTIVE,
              40,
              0.90,
              1,
              "en",
              "UTC",
              com.studydeck.domain.model.SchedulerAlgorithm.FSRS,
              Instant.EPOCH,
              Instant.EPOCH);
      userAccountRepo.save(account);

      // Create a NEW card
      NoteId note1 = NoteId.generate();
      noteRepo.save(Note.create(note1, capDeck, new NoteContent.Basic("Q1", "A1"), null));
      CardId newCard = CardId.generate();
      cardRepo.save(
          Card.create(
              newCard,
              note1,
              NoteType.BASIC,
              "forward",
              0,
              new CardPayload.BasicPrompt("Q1"),
              new CardPayload.BasicAnswer("A1")));
      scheduleRepo.save(
          capUser,
          newCard,
          CardScheduleState.newCard(
              com.studydeck.domain.model.SchedulerAlgorithm.FSRS, 0.9, clock.now()));

      // Create a REVIEW card (already graduated)
      NoteId note2 = NoteId.generate();
      noteRepo.save(Note.create(note2, capDeck, new NoteContent.Basic("Q2", "A2"), null));
      CardId reviewCard = CardId.generate();
      cardRepo.save(
          Card.create(
              reviewCard,
              note2,
              NoteType.BASIC,
              "forward",
              0,
              new CardPayload.BasicPrompt("Q2"),
              new CardPayload.BasicAnswer("A2")));
      scheduleRepo.save(
          capUser,
          reviewCard,
          new CardScheduleState(
              com.studydeck.domain.model.SchedulerAlgorithm.FSRS,
              CardState.REVIEW,
              5.0,
              5.0,
              0.9,
              1,
              0,
              1,
              clock.now(),
              clock.now().minusSeconds(86400)));

      // Simulate cap hit: 1 new card was already introduced today
      CardId alreadyIntroduced = CardId.generate();
      logRepo.save(
          capUser,
          null,
          new com.studydeck.domain.model.ReviewLog(
              alreadyIntroduced, ReviewRating.GOOD, CardState.NEW, clock.now(), 0, 1, null));

      UUID sessionId = sessionRepo.create(capUser, capDeck, 20, clock.now());

      GetNextCardQuery.Query query = new GetNextCardQuery.Query(capUser, sessionId);
      Optional<Card> result = sut.execute(query);

      assertThat(result).isPresent();
      assertThat(result.get().getId()).isEqualTo(reviewCard);
    }

    @Test
    @DisplayName("Cap hit: returns empty when no review cards available")
    void capHit_noReviewCards_returnsEmpty() {
      UserAccount account =
          UserAccount.reconstitute(
              capUser,
              "cap2@test.com",
              "Cap2",
              UserAccountStatus.ACTIVE,
              40,
              0.90,
              1,
              "en",
              "UTC",
              com.studydeck.domain.model.SchedulerAlgorithm.FSRS,
              Instant.EPOCH,
              Instant.EPOCH);
      userAccountRepo.save(account);

      // Only a NEW card
      NoteId note1 = NoteId.generate();
      noteRepo.save(Note.create(note1, capDeck, new NoteContent.Basic("Q1", "A1"), null));
      CardId newCard = CardId.generate();
      cardRepo.save(
          Card.create(
              newCard,
              note1,
              NoteType.BASIC,
              "forward",
              0,
              new CardPayload.BasicPrompt("Q1"),
              new CardPayload.BasicAnswer("A1")));
      scheduleRepo.save(
          capUser,
          newCard,
          CardScheduleState.newCard(
              com.studydeck.domain.model.SchedulerAlgorithm.FSRS, 0.9, clock.now()));

      // Cap hit: a new card was already introduced
      CardId alreadyIntroduced = CardId.generate();
      logRepo.save(
          capUser,
          null,
          new com.studydeck.domain.model.ReviewLog(
              alreadyIntroduced, ReviewRating.GOOD, CardState.NEW, clock.now(), 0, 1, null));

      UUID sessionId = sessionRepo.create(capUser, capDeck, 20, clock.now());

      GetNextCardQuery.Query query = new GetNextCardQuery.Query(capUser, sessionId);
      Optional<Card> result = sut.execute(query);

      assertThat(result).isEmpty();
    }
  }

  // ---------------------------------------------------------------
  // Preview Intervals
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("PreviewIntervals")
  class PreviewIntervalsTests {

    @Test
    @DisplayName("previewIntervals returns sensible values: again <= hard <= good <= easy")
    void previewIntervals_returnsOrderedValues() {
      OwnerId user = OwnerId.generate();
      UserAccount account =
          UserAccount.reconstitute(
              user,
              "preview@test.com",
              "Preview",
              UserAccountStatus.ACTIVE,
              40,
              0.90,
              10,
              "en",
              "UTC",
              com.studydeck.domain.model.SchedulerAlgorithm.FSRS,
              Instant.EPOCH,
              Instant.EPOCH);
      userAccountRepo.save(account);

      DeckId deck = DeckId.generate();
      deckRepo.save(Deck.create(deck, user, "Preview Deck", null));
      NoteId note = NoteId.generate();
      noteRepo.save(Note.create(note, deck, new NoteContent.Basic("Q", "A"), null));
      CardId card = CardId.generate();
      cardRepo.save(
          Card.create(
              card,
              note,
              NoteType.BASIC,
              "forward",
              0,
              new CardPayload.BasicPrompt("Q"),
              new CardPayload.BasicAnswer("A")));
      scheduleRepo.save(user, card, CardScheduleState.newFsrsCard(clock.now()));

      com.studydeck.domain.port.in.GetPreviewIntervalsQuery.PreviewIntervals intervals =
          sut.execute(new com.studydeck.domain.port.in.GetPreviewIntervalsQuery.Query(user, card));

      assertThat(intervals.again()).isGreaterThanOrEqualTo(0);
      assertThat(intervals.hard()).isGreaterThanOrEqualTo(intervals.again());
      assertThat(intervals.good()).isGreaterThanOrEqualTo(intervals.hard());
      assertThat(intervals.easy()).isGreaterThanOrEqualTo(intervals.good());
    }
  }
}
