package com.studydeck.application.support;

import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.out.ReviewSessionRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory test double for {@link ReviewSessionRepository}. */
public final class InMemoryReviewSessionRepository implements ReviewSessionRepository {

  private final Map<UUID, ReviewSessionView> store = new HashMap<>();

  @Override
  public UUID create(OwnerId ownerId, DeckId deckId, int maxCards, Instant startedAt) {
    UUID id = UUID.randomUUID();
    store.put(
        id, new ReviewSessionView(id, ownerId, deckId, maxCards, "started", startedAt, null, 0, 0));
    return id;
  }

  @Override
  public Optional<ReviewSessionView> findById(UUID sessionId) {
    return Optional.ofNullable(store.get(sessionId));
  }

  @Override
  public void incrementPresentedCount(UUID sessionId) {
    store.computeIfPresent(
        sessionId,
        (id, v) ->
            new ReviewSessionView(
                v.id(),
                v.ownerId(),
                v.deckId(),
                v.maxCards(),
                v.status(),
                v.startedAt(),
                v.endedAt(),
                v.presentedCount() + 1,
                v.answeredCount()));
  }

  @Override
  public void incrementAnsweredCount(UUID sessionId) {
    store.computeIfPresent(
        sessionId,
        (id, v) ->
            new ReviewSessionView(
                v.id(),
                v.ownerId(),
                v.deckId(),
                v.maxCards(),
                v.status(),
                v.startedAt(),
                v.endedAt(),
                v.presentedCount(),
                v.answeredCount() + 1));
  }

  @Override
  public void complete(UUID sessionId, Instant endedAt) {
    store.computeIfPresent(
        sessionId,
        (id, v) ->
            new ReviewSessionView(
                v.id(),
                v.ownerId(),
                v.deckId(),
                v.maxCards(),
                "completed",
                v.startedAt(),
                endedAt,
                v.presentedCount(),
                v.answeredCount()));
  }

  // Test helpers

  public int size() {
    return store.size();
  }

  public void clear() {
    store.clear();
  }
}
