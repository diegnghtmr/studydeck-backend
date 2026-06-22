package com.studydeck.application.service;

import com.studydeck.application.common.Page;
import com.studydeck.application.exception.NotFoundException;
import com.studydeck.domain.model.Card;
import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.CardScheduleState;
import com.studydeck.domain.model.CardState;
import com.studydeck.domain.model.Deck;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.Note;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.ReviewLog;
import com.studydeck.domain.model.ReviewResult;
import com.studydeck.domain.model.SchedulerAlgorithm;
import com.studydeck.domain.model.SchedulerPreset;
import com.studydeck.domain.port.in.GetDeckStatsQuery;
import com.studydeck.domain.port.in.GetNextCardQuery;
import com.studydeck.domain.port.in.GetReviewSessionQuery;
import com.studydeck.domain.port.in.ListDueCardsQuery;
import com.studydeck.domain.port.in.ListReviewHistoryQuery;
import com.studydeck.domain.port.in.StartReviewSessionUseCase;
import com.studydeck.domain.port.in.SubmitReviewUseCase;
import com.studydeck.domain.port.out.AuditEventPort;
import com.studydeck.domain.port.out.CardRepository;
import com.studydeck.domain.port.out.CardScheduleStateRepository;
import com.studydeck.domain.port.out.ClockPort;
import com.studydeck.domain.port.out.DeckRepository;
import com.studydeck.domain.port.out.NoteRepository;
import com.studydeck.domain.port.out.ReviewLogRepository;
import com.studydeck.domain.port.out.ReviewSessionRepository;
import com.studydeck.domain.port.out.ReviewSessionRepository.ReviewSessionView;
import com.studydeck.domain.service.FsrsScheduler;
import com.studydeck.domain.service.SchedulingEngine;
import com.studydeck.domain.service.Sm2Scheduler;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service implementing all review-related use cases.
 *
 * <p>Framework-free: no Spring annotations. Wired as {@code @Bean} in {@code BeanConfiguration}.
 *
 * <p>Algorithm resolution: looks up the deck's preset (or falls back to FSRS @0.9) → picks the
 * correct {@link SchedulingEngine} → calls it → persists new {@link CardScheduleState} + appends
 * {@link ReviewLog}.
 */
public final class ReviewService
    implements StartReviewSessionUseCase,
        GetReviewSessionQuery,
        GetNextCardQuery,
        SubmitReviewUseCase,
        ListDueCardsQuery,
        ListReviewHistoryQuery,
        GetDeckStatsQuery {

  private static final int DEFAULT_MAX_CARDS = 20;

  private final DeckRepository deckRepository;
  private final NoteRepository noteRepository;
  private final CardRepository cardRepository;
  private final CardScheduleStateRepository scheduleStateRepository;
  private final ReviewLogRepository reviewLogRepository;
  private final ReviewSessionRepository sessionRepository;
  private final AuditEventPort auditPort;
  private final ClockPort clockPort;

  private final FsrsScheduler fsrsScheduler;
  private final Sm2Scheduler sm2Scheduler;

  public ReviewService(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      CardScheduleStateRepository scheduleStateRepository,
      ReviewLogRepository reviewLogRepository,
      ReviewSessionRepository sessionRepository,
      AuditEventPort auditPort,
      ClockPort clockPort) {
    this.deckRepository = deckRepository;
    this.noteRepository = noteRepository;
    this.cardRepository = cardRepository;
    this.scheduleStateRepository = scheduleStateRepository;
    this.reviewLogRepository = reviewLogRepository;
    this.sessionRepository = sessionRepository;
    this.auditPort = auditPort;
    this.clockPort = clockPort;
    this.fsrsScheduler = new FsrsScheduler();
    this.sm2Scheduler = new Sm2Scheduler();
  }

  // ---------------------------------------------------------------
  // StartReviewSessionUseCase
  // ---------------------------------------------------------------

  @Override
  public UUID execute(StartReviewSessionUseCase.Command command) {
    // Validate deck ownership when deckId is provided
    if (command.deckId() != null) {
      findOwnedDeck(command.ownerId(), command.deckId());
    }
    Instant now = clockPort.now();
    UUID sessionId =
        sessionRepository.create(command.ownerId(), command.deckId(), command.maxCards(), now);
    auditPort.record(
        command.ownerId(), "review-session.created", "ReviewSession", sessionId.toString());
    return sessionId;
  }

  // ---------------------------------------------------------------
  // GetReviewSessionQuery
  // ---------------------------------------------------------------

  @Override
  public ReviewSessionView execute(GetReviewSessionQuery.Query query) {
    ReviewSessionView view =
        sessionRepository
            .findById(query.sessionId())
            .orElseThrow(
                () -> new NotFoundException("ReviewSession", query.sessionId().toString()));
    if (!view.ownerId().equals(query.ownerId())) {
      throw new NotFoundException("ReviewSession", query.sessionId().toString());
    }
    return view;
  }

  // ---------------------------------------------------------------
  // GetNextCardQuery
  // ---------------------------------------------------------------

  @Override
  public Optional<Card> execute(GetNextCardQuery.Query query) {
    ReviewSessionView session =
        execute(new GetReviewSessionQuery.Query(query.ownerId(), query.sessionId()));

    Instant now = clockPort.now();
    DeckId deckId = session.deckId();
    int limit = session.maxCards() - session.presentedCount();
    if (limit <= 0) {
      return Optional.empty();
    }

    List<CardId> dueIds = scheduleStateRepository.findDueCardIds(query.ownerId(), deckId, now, 1);
    if (dueIds.isEmpty()) {
      return Optional.empty();
    }

    CardId nextId = dueIds.get(0);
    Optional<Card> card = cardRepository.findById(nextId);
    if (card.isPresent()) {
      sessionRepository.incrementPresentedCount(query.sessionId());
    }
    return card;
  }

  // ---------------------------------------------------------------
  // SubmitReviewUseCase
  // ---------------------------------------------------------------

  @Override
  public SubmitReviewUseCase.Result execute(SubmitReviewUseCase.Command command) {
    // 1. Load card and verify ownership
    Card card = findOwnedCard(command.ownerId(), command.cardId());

    // 2. Load or default the current schedule state
    Instant now = clockPort.now();
    CardScheduleState current =
        scheduleStateRepository
            .findByCardId(command.cardId())
            .orElseGet(() -> CardScheduleState.newFsrsCard(now));

    // 3. Resolve scheduler preset for the deck
    SchedulerPreset preset = resolvePreset(card);

    // 4. Select the correct scheduling engine
    SchedulingEngine engine = engineFor(current.algorithm());

    // 5. Run the scheduling algorithm
    ReviewResult result =
        engine.schedule(current, command.rating(), now, preset.desiredRetention());

    // 6. Persist updated schedule state
    scheduleStateRepository.save(command.ownerId(), command.cardId(), result.nextState());

    // 7. Build and persist review log.
    // Build directly with the command's cardId — the SchedulingEngine returns a sentinel cardId
    // (zero UUID) since it is card-unaware by design (stateless computation).
    int elapsedDays = computeElapsedDays(current, now);
    ReviewLog log =
        new ReviewLog(
            command.cardId(),
            result.rating(),
            result.previousState().state(),
            result.reviewedAt(),
            elapsedDays,
            result.nextState().scheduledDays(),
            command.responseTimeMs());
    UUID historyEntryId = reviewLogRepository.save(command.ownerId(), command.sessionId(), log);

    // 8. Increment answered count when part of a session
    if (command.sessionId() != null) {
      sessionRepository.incrementAnsweredCount(command.sessionId());
    }

    // 9. Audit
    auditPort.record(command.ownerId(), "card.reviewed", "Card", command.cardId().toString());

    return new SubmitReviewUseCase.Result(result, historyEntryId);
  }

  // ---------------------------------------------------------------
  // ListDueCardsQuery
  // ---------------------------------------------------------------

  @Override
  public List<Card> execute(ListDueCardsQuery.Query query) {
    Instant now = clockPort.now();
    List<CardId> dueIds =
        scheduleStateRepository.findDueCardIds(query.ownerId(), query.deckId(), now, query.limit());
    return dueIds.stream()
        .map(id -> cardRepository.findById(id))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();
  }

  // ---------------------------------------------------------------
  // ListReviewHistoryQuery
  // ---------------------------------------------------------------

  @Override
  public Page<ReviewLog> execute(ListReviewHistoryQuery.Query query) {
    int offset = query.pageRequest().offset();
    int limit = query.pageRequest().size();
    int page = query.pageRequest().page();

    List<ReviewLog> content =
        reviewLogRepository.findHistory(
            query.ownerId(),
            query.deckId(),
            query.cardId(),
            query.from(),
            query.to(),
            offset,
            limit);
    long total =
        reviewLogRepository.countHistory(
            query.ownerId(), query.deckId(), query.cardId(), query.from(), query.to());
    return Page.of(content, page, limit, total);
  }

  // ---------------------------------------------------------------
  // GetDeckStatsQuery
  // ---------------------------------------------------------------

  @Override
  public GetDeckStatsQuery.DeckStatsResult execute(GetDeckStatsQuery.Query query) {
    findOwnedDeck(query.ownerId(), query.deckId());

    Instant now = clockPort.now();
    Instant dayStart = now.truncatedTo(ChronoUnit.DAYS);
    Instant dayEnd = dayStart.plus(Duration.ofDays(1));
    Instant sevenDaysAgo = now.minus(Duration.ofDays(7));
    Instant thirtyDaysAgo = now.minus(Duration.ofDays(30));

    // Total notes and cards
    long totalNotesL = noteRepository.countAll(query.deckId(), null, null, null);
    long totalCardsL = cardRepository.countAll(query.deckId(), null);
    long dueCountL =
        scheduleStateRepository
            .findDueCardIds(query.ownerId(), query.deckId(), now, Integer.MAX_VALUE)
            .size();
    long newCardsL = scheduleStateRepository.countNewByDeck(query.ownerId(), query.deckId());
    int reviewedToday =
        reviewLogRepository.countReviewedToday(query.ownerId(), query.deckId(), dayStart, dayEnd);
    long suspendedL = cardRepository.countAll(query.deckId(), true);

    Double againRate =
        reviewLogRepository.againRate7d(query.ownerId(), query.deckId(), sevenDaysAgo);
    Double avgRetention =
        reviewLogRepository.averageRetention30d(query.ownerId(), query.deckId(), thirtyDaysAgo);

    return new GetDeckStatsQuery.DeckStatsResult(
        query.deckId(),
        (int) totalNotesL,
        (int) totalCardsL,
        (int) dueCountL,
        (int) newCardsL,
        reviewedToday,
        (int) suspendedL,
        againRate,
        avgRetention);
  }

  // ---------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------

  private Deck findOwnedDeck(OwnerId ownerId, DeckId deckId) {
    Deck deck =
        deckRepository
            .findById(deckId)
            .orElseThrow(() -> new NotFoundException("Deck", deckId.toString()));
    if (!deck.getOwnerId().equals(ownerId)) {
      throw new NotFoundException("Deck", deckId.toString());
    }
    return deck;
  }

  private Card findOwnedCard(OwnerId ownerId, CardId cardId) {
    Card card =
        cardRepository
            .findById(cardId)
            .orElseThrow(() -> new NotFoundException("Card", cardId.toString()));
    // verify ownership via note → deck chain
    Note note =
        noteRepository
            .findById(card.getNoteId())
            .orElseThrow(() -> new NotFoundException("Card", cardId.toString()));
    Deck deck =
        deckRepository
            .findById(note.getDeckId())
            .orElseThrow(() -> new NotFoundException("Card", cardId.toString()));
    if (!deck.getOwnerId().equals(ownerId)) {
      throw new NotFoundException("Card", cardId.toString());
    }
    return card;
  }

  private SchedulerPreset resolvePreset(Card card) {
    // Future: look up deck → scheduler_preset link. For now default to FSRS @ 0.9.
    return SchedulerPreset.fsrsDefault();
  }

  private SchedulingEngine engineFor(SchedulerAlgorithm algorithm) {
    return switch (algorithm) {
      case FSRS -> fsrsScheduler;
      case SM2 -> sm2Scheduler;
    };
  }

  private int computeElapsedDays(CardScheduleState current, Instant now) {
    if (current.lastReviewedAt() == null || current.state() == CardState.NEW) {
      return 0;
    }
    long seconds = Duration.between(current.lastReviewedAt(), now).getSeconds();
    return (int) Math.max(0, seconds / 86400L);
  }
}
