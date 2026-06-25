package com.studydeck.application.support;

import com.studydeck.domain.model.Card;
import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.NoteId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.out.CardRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * In-memory test double for {@link CardRepository}.
 *
 * <p>Deck-level and owner-level filtering require resolvers because {@link Card} holds a {@code
 * noteId} (not a {@code deckId}). Inject {@code noteIdToDeckId} for deck filtering and {@code
 * noteIdToOwnerId} for ownership filtering. When a resolver is not provided the corresponding
 * filter is skipped (all cards pass), keeping backward-compatible behaviour for tests that do not
 * need cross-tenant isolation.
 */
public final class InMemoryCardRepository implements CardRepository {

  private final Map<CardId, Card> store = new ConcurrentHashMap<>();

  /**
   * Optional resolver: given a NoteId returns the DeckId it belongs to. Used for deck-based
   * filtering in findAll.
   */
  private Function<NoteId, DeckId> noteIdToDeckId = noteId -> null;

  /**
   * Optional resolver: given a NoteId returns the OwnerId of the deck that owns it. Used for
   * owner-based filtering in findAll / countAll.
   */
  private Function<NoteId, OwnerId> noteIdToOwnerId = noteId -> null;

  public void setNoteIdToDeckId(Function<NoteId, DeckId> resolver) {
    this.noteIdToDeckId = resolver;
  }

  public void setNoteIdToOwnerId(Function<NoteId, OwnerId> resolver) {
    this.noteIdToOwnerId = resolver;
  }

  @Override
  public void saveAll(List<Card> cards) {
    cards.forEach(c -> store.put(c.getId(), c));
  }

  @Override
  public void save(Card card) {
    store.put(card.getId(), card);
  }

  @Override
  public Optional<Card> findById(CardId id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<Card> findByNoteId(NoteId noteId) {
    return store.values().stream()
        .filter(c -> c.getNoteId().equals(noteId))
        .sorted(java.util.Comparator.comparingInt(Card::getOrdinal))
        .toList();
  }

  @Override
  public List<Card> findAll(
      OwnerId ownerId, DeckId deckId, Boolean suspended, int offset, int limit) {
    return store.values().stream()
        .filter(c -> matchesOwner(c, ownerId))
        .filter(c -> deckId == null || deckId.equals(noteIdToDeckId.apply(c.getNoteId())))
        .filter(c -> suspended == null || c.isSuspended() == suspended)
        .sorted(
            java.util.Comparator.comparing(Card::getCreatedAt)
                .thenComparing(c -> c.getId().value()))
        .skip(offset)
        .limit(limit)
        .toList();
  }

  @Override
  public long countAll(OwnerId ownerId, DeckId deckId, Boolean suspended) {
    return store.values().stream()
        .filter(c -> matchesOwner(c, ownerId))
        .filter(c -> deckId == null || deckId.equals(noteIdToDeckId.apply(c.getNoteId())))
        .filter(c -> suspended == null || c.isSuspended() == suspended)
        .count();
  }

  @Override
  public void deleteByNoteId(NoteId noteId) {
    store.entrySet().removeIf(e -> e.getValue().getNoteId().equals(noteId));
  }

  @Override
  public void deleteById(CardId id) {
    store.remove(id);
  }

  // --- Test helpers ---

  public int size() {
    return store.size();
  }

  public void clear() {
    store.clear();
  }

  public List<Card> all() {
    return new ArrayList<>(store.values());
  }

  // --- Private helpers ---

  private boolean matchesOwner(Card card, OwnerId ownerId) {
    if (ownerId == null) {
      return true;
    }
    OwnerId resolved = noteIdToOwnerId.apply(card.getNoteId());
    // When no resolver is configured (resolved == null), let the card pass
    // so existing tests that don't set up owner resolution still work.
    if (resolved == null) {
      return true;
    }
    return resolved.equals(ownerId);
  }
}
