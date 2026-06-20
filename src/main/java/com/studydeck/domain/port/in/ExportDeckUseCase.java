package com.studydeck.domain.port.in;

import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;

/**
 * Input port — exports a deck's notes to the versioned FlashcardImportV1 JSON payload shape.
 *
 * <p>The result is round-trippable: export then import must yield equivalent notes.
 */
public interface ExportDeckUseCase {

  ValidateImportUseCase.ImportPayload execute(Command command);

  record Command(OwnerId ownerId, DeckId deckId) {}
}
