package com.studydeck.domain.port.in;

import com.studydeck.domain.model.AiProviderConfig;
import com.studydeck.domain.model.OwnerId;

/**
 * Input port: improve an existing or proposed flashcard using AI.
 *
 * <p>Output is validated against FlashcardImportV1 JSON Schema before being returned. The improved
 * card is a proposal — it still requires human approval before persistence.
 */
public interface ImproveFlashcardUseCase {

  Result execute(Command command);

  record Command(
      OwnerId ownerId,
      String noteType,
      String currentContentJson,
      String instruction,
      AiProviderConfig providerConfig) {}

  /**
   * @param improvedJson validated improved card content JSON
   * @param noteType the note type of the improved card
   */
  record Result(String noteType, String improvedJson) {}
}
