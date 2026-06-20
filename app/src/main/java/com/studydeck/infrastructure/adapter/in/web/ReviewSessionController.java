package com.studydeck.infrastructure.adapter.in.web;

import com.studydeck.domain.model.Card;
import com.studydeck.domain.model.CardScheduleState;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.GetNextCardQuery;
import com.studydeck.domain.port.in.GetReviewSessionQuery;
import com.studydeck.domain.port.in.StartReviewSessionUseCase;
import com.studydeck.domain.port.out.CardScheduleStateRepository;
import com.studydeck.domain.port.out.ReviewSessionRepository.ReviewSessionView;
import com.studydeck.infrastructure.adapter.in.web.dto.CardResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.NextReviewCardResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.ReviewSessionCreateRequest;
import com.studydeck.infrastructure.adapter.in.web.dto.ReviewSessionResponse;
import com.studydeck.infrastructure.adapter.in.web.mapper.CardWebMapper;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driving adapter — REST controller for review session operations.
 *
 * <p>POST /v1/review-sessions → 201 StartReviewSession GET /v1/review-sessions/{id} → 200
 * GetReviewSession GET /v1/review-sessions/{id}/next → 200 with card | 204 when empty
 */
@RestController
@RequestMapping("/v1/review-sessions")
class ReviewSessionController {

  private final StartReviewSessionUseCase startSession;
  private final GetReviewSessionQuery getSession;
  private final GetNextCardQuery getNextCard;
  private final CardScheduleStateRepository scheduleStateRepository;
  private final CardWebMapper cardMapper;

  ReviewSessionController(
      @Qualifier("startReviewSessionUseCase") StartReviewSessionUseCase startSession,
      @Qualifier("getReviewSessionQuery") GetReviewSessionQuery getSession,
      @Qualifier("getNextCardQuery") GetNextCardQuery getNextCard,
      CardScheduleStateRepository scheduleStateRepository,
      CardWebMapper cardMapper) {
    this.startSession = startSession;
    this.getSession = getSession;
    this.getNextCard = getNextCard;
    this.scheduleStateRepository = scheduleStateRepository;
    this.cardMapper = cardMapper;
  }

  @PostMapping
  ResponseEntity<ReviewSessionResponse> createSession(
      @RequestBody ReviewSessionCreateRequest request, @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = new OwnerId(UUID.fromString(jwt.getSubject()));
    DeckId deckId = request.deckId() != null ? new DeckId(request.deckId()) : null;
    int maxCards = request.maxCards() != null ? request.maxCards() : 20;

    UUID sessionId =
        startSession.execute(new StartReviewSessionUseCase.Command(ownerId, deckId, maxCards));
    ReviewSessionView view =
        getSession.execute(new GetReviewSessionQuery.Query(ownerId, sessionId));
    URI location = URI.create("/v1/review-sessions/" + sessionId);
    return ResponseEntity.created(location).body(toResponse(view));
  }

  @GetMapping("/{sessionId}")
  ResponseEntity<ReviewSessionResponse> getSession(
      @PathVariable UUID sessionId, @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = new OwnerId(UUID.fromString(jwt.getSubject()));
    ReviewSessionView view =
        getSession.execute(new GetReviewSessionQuery.Query(ownerId, sessionId));
    return ResponseEntity.ok(toResponse(view));
  }

  @GetMapping("/{sessionId}/next")
  ResponseEntity<NextReviewCardResponse> getNextCard(
      @PathVariable UUID sessionId, @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = new OwnerId(UUID.fromString(jwt.getSubject()));
    Optional<Card> next = getNextCard.execute(new GetNextCardQuery.Query(ownerId, sessionId));
    if (next.isEmpty()) {
      return ResponseEntity.noContent().build();
    }
    Card card = next.get();
    // Load schedule state to enrich the card response
    Optional<CardScheduleState> state = scheduleStateRepository.findByCardId(card.getId());
    // Derive deckId from card's noteId via session; we pass the session's deckId as the deckId
    ReviewSessionView session =
        getSession.execute(new GetReviewSessionQuery.Query(ownerId, sessionId));
    UUID deckId = session.deckId() != null ? session.deckId().value() : null;
    CardResponse cardResponse = cardMapper.toResponse(card, deckId, state.orElse(null));
    return ResponseEntity.ok(new NextReviewCardResponse(sessionId, cardResponse));
  }

  private ReviewSessionResponse toResponse(ReviewSessionView view) {
    return new ReviewSessionResponse(
        view.id(),
        view.deckId() != null ? view.deckId().value() : null,
        view.status(),
        view.startedAt(),
        view.endedAt(),
        view.presentedCount(),
        view.answeredCount());
  }
}
