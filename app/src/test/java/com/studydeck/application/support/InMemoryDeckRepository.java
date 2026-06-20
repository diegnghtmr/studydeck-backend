package com.studydeck.application.support;

import com.studydeck.domain.model.Deck;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.out.DeckRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory test double for {@link DeckRepository}. Thread-safe. */
public final class InMemoryDeckRepository implements DeckRepository {

  private final Map<DeckId, Deck> store = new ConcurrentHashMap<>();

  @Override
  public void save(Deck deck) {
    store.put(deck.getId(), deck);
  }

  @Override
  public Optional<Deck> findById(DeckId id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<Deck> findByOwner(
      OwnerId ownerId, boolean includeArchived, String search, int offset, int limit) {
    return store.values().stream()
        .filter(d -> d.getOwnerId().equals(ownerId))
        .filter(d -> includeArchived || !d.isArchived())
        .filter(d -> matchesSearch(d, search))
        .sorted(
            java.util.Comparator.comparing(Deck::getCreatedAt)
                .thenComparing(d -> d.getId().value()))
        .skip(offset)
        .limit(limit)
        .toList();
  }

  @Override
  public long countByOwner(OwnerId ownerId, boolean includeArchived, String search) {
    return store.values().stream()
        .filter(d -> d.getOwnerId().equals(ownerId))
        .filter(d -> includeArchived || !d.isArchived())
        .filter(d -> matchesSearch(d, search))
        .count();
  }

  @Override
  public void deleteById(DeckId id) {
    store.remove(id);
  }

  // --- Test helpers ---

  public int size() {
    return store.size();
  }

  public void clear() {
    store.clear();
  }

  public List<Deck> all() {
    return new ArrayList<>(store.values());
  }

  // --- Private helpers ---

  private boolean matchesSearch(Deck deck, String search) {
    if (search == null || search.isBlank()) {
      return true;
    }
    String lower = search.toLowerCase();
    return deck.getTitle().toLowerCase().contains(lower)
        || (deck.getDescription() != null && deck.getDescription().toLowerCase().contains(lower));
  }
}
