package com.studydeck.domain.port.in;

import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import java.util.List;
import java.util.UUID;

/**
 * Input port — executes a flashcard import: persists accepted notes via the existing note-creation
 * path (cards generated, schedule state initialised, audit recorded).
 *
 * <p>Duplicate notes (same content hash in target deck) are skipped. Domain-invalid notes are
 * rejected (returned in warnings). Returns an {@link ImportResult} with counts and the import job
 * id.
 */
public interface ExecuteImportUseCase {

  ImportResult execute(Command command);

  /**
   * @param targetDeckId when null, a new deck is created from payload.deck.title
   */
  record Command(
      OwnerId ownerId, ValidateImportUseCase.ImportPayload payload, DeckId targetDeckId) {}

  record ImportResult(
      UUID importId,
      UUID deckId,
      int importedNotes,
      int importedCards,
      int duplicateNotes,
      int rejectedNotes,
      List<String> warnings) {}
}
