package com.studydeck.domain.port.in;

import com.studydeck.domain.model.AiProviderConfig;
import com.studydeck.domain.model.OwnerId;
import java.util.List;

/**
 * Input port: generate flashcard proposals from source text using AI structured output.
 *
 * <p>Generated cards are PROPOSALS — they must be validated against FlashcardImportV1 JSON Schema
 * server-side before being returned to the caller. They are NOT persisted automatically; the caller
 * must route them through the import/approve path for persistence.
 */
public interface GenerateFlashcardsUseCase {

  Result execute(Command command);

  record Command(
      OwnerId ownerId,
      String sourceText,
      String deckContext,
      List<String> noteTypes,
      int maxCards,
      AiProviderConfig providerConfig) {}

  /**
   * @param proposalsJson validated FlashcardImportV1-compatible JSON string
   * @param requiresApproval always true — proposals never auto-persist
   */
  record Result(String proposalsJson, boolean requiresApproval) {}
}
