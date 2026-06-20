package com.studydeck.domain.port.in;

import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import java.util.List;

/**
 * Input port — validates + detects duplicates for an import payload against an existing deck.
 *
 * <p>Does NOT persist anything. Returns a preview: per-item status (valid / invalid / duplicate)
 * plus summary counts.
 */
public interface PreviewImportUseCase {

  Result execute(Command command);

  /**
   * targetDeckId is optional: when null the deck is looked up by title or a new deck is assumed.
   */
  record Command(
      OwnerId ownerId, ValidateImportUseCase.ImportPayload payload, DeckId targetDeckId) {}

  record Result(
      boolean valid,
      Summary summary,
      ValidateImportUseCase.ImportPayload normalizedPayload,
      List<String> warnings) {

    public record Summary(
        String deckTitle, int totalNotes, int predictedCards, int duplicateCandidates) {}
  }
}
