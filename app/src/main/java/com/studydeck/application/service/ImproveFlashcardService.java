package com.studydeck.application.service;

import com.studydeck.domain.port.in.ImproveFlashcardUseCase;
import com.studydeck.domain.port.out.AiChatPort;
import com.studydeck.domain.port.out.AiSchemaValidationPort;

/**
 * Application service implementing the ImproveFlashcard use case.
 *
 * <p>AI-generated improved content is validated server-side against the FlashcardImportV1 JSON
 * Schema before being returned.
 *
 * <p>Framework-free: no Spring annotations.
 */
public final class ImproveFlashcardService implements ImproveFlashcardUseCase {

  private final AiChatPort chatPort;
  private final AiSchemaValidationPort schemaValidator;

  public ImproveFlashcardService(AiChatPort chatPort, AiSchemaValidationPort schemaValidator) {
    this.chatPort = chatPort;
    this.schemaValidator = schemaValidator;
  }

  @Override
  public Result execute(Command command) {
    // A per-request BYOK override (providerConfig) carries its own baseUrl + apiKey + model,
    // so it can reach a model even when no global provider is configured. Only reject when
    // there is no override AND no global provider available.
    if (command.providerConfig() == null && !chatPort.isAvailable()) {
      throw new AiChatPort.AiChatUnavailableException();
    }

    String rawJson =
        chatPort.improveFlashcardRaw(
            command.noteType(),
            command.currentContentJson(),
            command.instruction(),
            command.providerConfig());

    // MANDATORY: validate against schema. The improve prompt returns BARE note content (no
    // envelope, no noteType discriminator), so validate the single note — not the full envelope.
    String validatedJson = schemaValidator.validateNoteAndReturn(command.noteType(), rawJson);

    return new Result(command.noteType(), validatedJson);
  }
}
