package com.studydeck.domain.port.out;

import com.studydeck.domain.model.Deck;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import java.util.List;
import java.util.Optional;

/**
 * Output port — persistence contract for Deck aggregates.
 *
 * <p>Named after what the use cases NEED, not the technology behind it.
 */
public interface DeckRepository {

  /** Persists a new or updated Deck. */
  void save(Deck deck);

  /** Loads a Deck by its id, regardless of owner. Returns empty when not found. */
  Optional<Deck> findById(DeckId id);

  /**
   * Loads all Decks owned by the given owner, with optional filters.
   *
   * @param ownerId the owner
   * @param includeArchived when false, archived decks are excluded
   * @param search optional name/description substring filter; null or blank means no filter
   * @param offset page offset (0-based)
   * @param limit page size
   * @return ordered slice of decks (by createdAt asc)
   */
  List<Deck> findByOwner(
      OwnerId ownerId, boolean includeArchived, String search, int offset, int limit);

  /**
   * Counts Decks owned by the given owner matching the same filters (for pagination metadata).
   *
   * @param ownerId the owner
   * @param includeArchived when false, archived decks are excluded
   * @param search optional filter; null or blank means no filter
   */
  long countByOwner(OwnerId ownerId, boolean includeArchived, String search);

  /** Deletes a Deck by id. No-op when the deck does not exist. */
  void deleteById(DeckId id);
}
