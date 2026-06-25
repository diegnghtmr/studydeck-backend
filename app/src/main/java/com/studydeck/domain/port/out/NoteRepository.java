package com.studydeck.domain.port.out;

import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.Note;
import com.studydeck.domain.model.NoteId;
import com.studydeck.domain.model.NoteType;
import com.studydeck.domain.model.OwnerId;
import java.util.List;
import java.util.Optional;

/** Output port — persistence contract for Note aggregates. */
public interface NoteRepository {

  /** Persists a new or updated Note. */
  void save(Note note);

  /** Loads a Note by its id. Returns empty when not found. */
  Optional<Note> findById(NoteId id);

  /**
   * Loads Notes with optional filters, scoped to the authenticated owner.
   *
   * @param ownerId the authenticated owner (non-null); results are restricted to this owner's decks
   * @param deckId optional deck filter; null means all decks owned by ownerId
   * @param noteType optional type filter; null means all types
   * @param tag optional tag filter; null means no tag filter
   * @param search optional text search over note content; null or blank means no filter
   * @param offset page offset (0-based)
   * @param limit page size
   */
  List<Note> findAll(
      OwnerId ownerId,
      DeckId deckId,
      NoteType noteType,
      String tag,
      String search,
      int offset,
      int limit);

  /**
   * Counts Notes matching the same filters, scoped to the authenticated owner.
   *
   * @param ownerId the authenticated owner (non-null); results are restricted to this owner's decks
   * @param deckId optional deck filter; null means all decks owned by ownerId
   * @param noteType optional type filter; null means all types
   * @param tag optional tag filter; null means no tag filter
   * @param search optional text search; null or blank means no filter
   */
  long countAll(OwnerId ownerId, DeckId deckId, NoteType noteType, String tag, String search);

  /** Deletes a Note by id. No-op when the note does not exist. */
  void deleteById(NoteId id);
}
