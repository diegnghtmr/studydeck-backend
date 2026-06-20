package com.studydeck.infrastructure.adapter.in.web;

import com.studydeck.application.common.Page;
import com.studydeck.application.common.PageRequest;
import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.CardScheduleState;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.ReviewLog;
import com.studydeck.domain.model.ReviewRating;
import com.studydeck.domain.model.ReviewResult;
import com.studydeck.domain.port.in.ListReviewHistoryQuery;
import com.studydeck.domain.port.in.SubmitReviewUseCase;
import com.studydeck.infrastructure.adapter.in.web.dto.FSRSReviewResultResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.PageMetaResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.PagedResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.ReviewLogResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.ReviewSubmitRequest;
import com.studydeck.infrastructure.adapter.in.web.dto.SchedulerStateResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driving adapter — REST controller for review submission and history.
 *
 * <p>POST /v1/reviews → 200 SubmitReview (FSRSReviewResult) GET /v1/reviews/history → 200 paginated
 * ReviewLog
 */
@RestController
@RequestMapping("/v1/reviews")
class ReviewController {

  private final SubmitReviewUseCase submitReview;
  private final ListReviewHistoryQuery listHistory;

  ReviewController(
      @Qualifier("submitReviewUseCase") SubmitReviewUseCase submitReview,
      @Qualifier("listReviewHistoryQuery") ListReviewHistoryQuery listHistory) {
    this.submitReview = submitReview;
    this.listHistory = listHistory;
  }

  @PostMapping
  ResponseEntity<FSRSReviewResultResponse> submitReview(
      @Valid @RequestBody ReviewSubmitRequest request, @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = new OwnerId(UUID.fromString(jwt.getSubject()));
    ReviewRating rating = parseRating(request.rating());

    SubmitReviewUseCase.Command command =
        new SubmitReviewUseCase.Command(
            ownerId,
            new CardId(request.cardId()),
            rating,
            request.sessionId(),
            request.responseTimeMs());

    SubmitReviewUseCase.Result result = submitReview.execute(command);
    ReviewResult reviewResult = result.reviewResult();

    FSRSReviewResultResponse response =
        new FSRSReviewResultResponse(
            reviewResult.cardId().value(),
            request.sessionId(),
            toRatingString(reviewResult.rating()),
            reviewResult.reviewedAt(),
            toSchedulerStateResponse(reviewResult.previousState(), null),
            toSchedulerStateResponse(reviewResult.nextState(), null),
            result.historyEntryId());

    return ResponseEntity.ok(response);
  }

  @GetMapping("/history")
  ResponseEntity<PagedResponse<ReviewLogResponse>> listHistory(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) UUID deckId,
      @RequestParam(required = false) UUID cardId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = new OwnerId(UUID.fromString(jwt.getSubject()));

    ListReviewHistoryQuery.Query query =
        new ListReviewHistoryQuery.Query(
            ownerId,
            deckId != null ? new DeckId(deckId) : null,
            cardId != null ? new CardId(cardId) : null,
            from,
            to,
            PageRequest.of(page, size));

    Page<ReviewLog> result = listHistory.execute(query);
    List<ReviewLogResponse> items = result.content().stream().map(this::toLogResponse).toList();
    long totalPages = result.totalPages();
    var meta =
        new PageMetaResponse(
            result.page(),
            result.size(),
            result.totalElements(),
            totalPages,
            result.hasNext(),
            result.page() > 0);
    return ResponseEntity.ok(new PagedResponse<>(items, meta));
  }

  // ---------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------

  /** Map OpenAPI lowercase rating to domain enum. */
  private ReviewRating parseRating(String rating) {
    return switch (rating.toLowerCase()) {
      case "again" -> ReviewRating.AGAIN;
      case "hard" -> ReviewRating.HARD;
      case "good" -> ReviewRating.GOOD;
      case "easy" -> ReviewRating.EASY;
      default -> throw new IllegalArgumentException("Unknown rating: " + rating);
    };
  }

  /** Map domain enum to OpenAPI lowercase string. */
  private String toRatingString(ReviewRating rating) {
    return rating.name().toLowerCase();
  }

  private SchedulerStateResponse toSchedulerStateResponse(
      CardScheduleState state, Integer elapsedDays) {
    if (state == null) return null;
    return new SchedulerStateResponse(
        state.dueAt(),
        state.stability(),
        state.difficulty(),
        null, // retrievability — compute on demand in future enhancement
        elapsedDays,
        state.scheduledDays(),
        state.desiredRetention());
  }

  private ReviewLogResponse toLogResponse(ReviewLog log) {
    return new ReviewLogResponse(
        log.cardId().value(),
        toRatingString(log.rating()),
        log.stateBefore().name().toLowerCase(),
        log.reviewedAt(),
        log.elapsedDays(),
        log.scheduledDays(),
        log.responseTimeMs());
  }
}
