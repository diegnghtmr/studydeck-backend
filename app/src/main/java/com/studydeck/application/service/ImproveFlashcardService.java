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
    if (!chatPort.isAvailable()) {
      throw new AiChatPort.AiChatUnavailableException();
    }

    String rawJson =
        chatPort.improveFlashcardRaw(
            command.noteType(), command.currentContentJson(), command.instruction());

    // MANDATORY: validate against schema.
    String validatedJson = schemaValidator.validateAndReturn(rawJson);

    return new Result(command.noteType(), validatedJson);
  }
}
