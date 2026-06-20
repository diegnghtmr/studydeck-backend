package com.studydeck.domain.port.out;

import com.studydeck.domain.model.Card;
import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.NoteId;
import java.util.List;
import java.util.Optional;

/** Output port — persistence contract for Card entities. */
public interface CardRepository {

  /** Persists one or more cards (batch insert on create, update on edit). */
  void saveAll(List<Card> cards);

  /** Persists a single card. */
  void save(Card card);

  /** Loads a Card by its id. Returns empty when not found. */
  Optional<Card> findById(CardId id);

  /**
   * Loads all Cards derived from the given Note, ordered by ordinal asc.
   *
   * @param noteId the source note
   */
  List<Card> findByNoteId(NoteId noteId);

  /**
   * Loads Cards with optional filters.
   *
   * @param deckId optional deck filter; null means all decks
   * @param suspended optional suspended filter; null means all
   * @param offset page offset (0-based)
   * @param limit page size
   */
  List<Card> findAll(DeckId deckId, Boolean suspended, int offset, int limit);

  /**
   * Counts Cards matching the same filters.
   *
   * @param deckId optional deck filter; null means all decks
   * @param suspended optional suspended filter; null means all
   */
  long countAll(DeckId deckId, Boolean suspended);

  /** Deletes all Cards derived from a Note. Used when a Note is deleted. */
  void deleteByNoteId(NoteId noteId);

  /** Deletes a single Card by id. No-op when the card does not exist. */
  void deleteById(CardId id);
}
