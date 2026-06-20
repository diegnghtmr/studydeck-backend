package com.studydeck.domain.port.out;

import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import java.util.List;
import java.util.UUID;

/** Output port — persistence contract for import_job rows. */
public interface ImportJobRepository {

  /**
   * Persists a completed import job and returns the generated job id.
   *
   * @param ownerId actor who performed the import
   * @param deckId target deck (nullable when deck creation failed)
   * @param schemaVersion payload schemaVersion
   * @param importedNotes count of notes created
   * @param importedCards count of cards created
   * @param duplicateNotes count of notes skipped as duplicates
   * @param rejectedNotes count of notes rejected due to domain errors
   * @param warnings per-item warning strings
   */
  UUID save(
      OwnerId ownerId,
      DeckId deckId,
      String schemaVersion,
      int importedNotes,
      int importedCards,
      int duplicateNotes,
      int rejectedNotes,
      List<String> warnings);
}
