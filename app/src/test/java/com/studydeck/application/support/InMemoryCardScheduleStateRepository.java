package com.studydeck.application.support;

import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.CardScheduleState;
import com.studydeck.domain.model.CardState;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.out.CardScheduleStateRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory test double for {@link CardScheduleStateRepository}. */
public final class InMemoryCardScheduleStateRepository implements CardScheduleStateRepository {

  /** Stores (ownerId, state) keyed by cardId. */
  private final Map<CardId, StoredEntry> store = new HashMap<>();

  @Override
  public void save(OwnerId ownerId, CardId cardId, CardScheduleState state) {
    store.put(cardId, new StoredEntry(ownerId, state));
  }

  @Override
  public Optional<CardScheduleState> findByCardId(CardId cardId) {
    StoredEntry entry = store.get(cardId);
    return entry == null ? Optional.empty() : Optional.of(entry.state());
  }

  @Override
  public List<CardId> findDueCardIds(OwnerId ownerId, DeckId deckId, Instant dueAt, int limit) {
    return store.entrySet().stream()
        .filter(e -> e.getValue().ownerId().equals(ownerId))
        .filter(e -> !e.getValue().state().dueAt().isAfter(dueAt))
        .map(Map.Entry::getKey)
        .limit(limit)
        .toList();
  }

  @Override
  public long countDueGlobal(OwnerId ownerId, Instant now) {
    return store.entrySet().stream()
        .filter(e -> e.getValue().ownerId().equals(ownerId))
        .filter(e -> !e.getValue().state().dueAt().isAfter(now))
        .count();
  }

  @Override
  public long countNewGlobal(OwnerId ownerId) {
    return store.entrySet().stream()
        .filter(e -> e.getValue().ownerId().equals(ownerId))
        .filter(e -> e.getValue().state().state() == com.studydeck.domain.model.CardState.NEW)
        .count();
  }

  @Override
  public long countNewByDeck(OwnerId ownerId, DeckId deckId) {
    // The double does not track per-card deck association (see findDueCardIds), so this mirrors
    // countNewGlobal scoped to the owner. Deck-accurate counting is covered by the JPA adapter
    // test.
    return countNewGlobal(ownerId);
  }

  @Override
  public List<CardId> findDueReviewCardIds(
      OwnerId ownerId, DeckId deckId, Instant dueAt, int limit) {
    return store.entrySet().stream()
        .filter(e -> e.getValue().ownerId().equals(ownerId))
        .filter(e -> !e.getValue().state().dueAt().isAfter(dueAt))
        .filter(e -> e.getValue().state().state() != CardState.NEW)
        .map(Map.Entry::getKey)
        .limit(limit)
        .toList();
  }

  // Test helpers

  public int size() {
    return store.size();
  }

  public void clear() {
    store.clear();
  }

  public List<CardId> allCardIds() {
    return new ArrayList<>(store.keySet());
  }

  private record StoredEntry(OwnerId ownerId, CardScheduleState state) {}
}
