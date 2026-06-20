package com.studydeck.domain.port.out;

import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.NoteId;
import com.studydeck.domain.model.NoteType;
import java.util.Set;

/**
 * Output port — persistence contract for content-hash–based duplicate detection.
 *
 * <p>The hash is a SHA-256 hex string of the normalized note content JSON, scoped to (deck_id,
 * note_type). This supports exact-match dedup (P3 scope).
 */
public interface NoteHashRepository {

  /**
   * Returns the set of content hashes already present in the given deck for the given note type.
   * Used by preview and execute import to flag duplicates before persisting.
   *
   * @param deckId target deck
   * @param noteType note type to scope the lookup
   */
  Set<String> findExistingHashes(DeckId deckId, NoteType noteType);

  /**
   * Persists the content hash for a newly created note.
   *
   * @param noteId the persisted note
   * @param hash SHA-256 hex of normalized content
   */
  void saveHash(NoteId noteId, String hash);
}
