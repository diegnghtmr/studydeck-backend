package com.studydeck.application.service;

import com.studydeck.domain.port.in.ImproveFlashcardUseCase;
import com.studydeck.domain.port.out.AiChatPort;
import com.studydeck.domain.port.out.AiSchemaValidationPort;
import java.util.Objects;

/**
 * Application service implementing the ImproveFlashcard use case.
 *
 * <p>AI-generated improved content is validated server-side against the FlashcardImportV1 JSON
 * Schema before being returned.
 *
 * <p>Framework-free: no Spring annotations.
 */
public final class ImproveFlashcardService implements ImproveFlashcardUseCase {

  /**
   * Total attempts (1 initial + retries) when the model output violates the schema. Reasoning
   * models are non-deterministic, so re-asking usually yields conformant JSON on a later try.
   */
  private static final int MAX_AI_ATTEMPTS = 3;

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

    // Re-ask the model on schema violations only (non-deterministic reasoning output). Any other
    // failure (e.g. AiChatUnavailableException) propagates immediately and is never retried.
    AiSchemaValidationPort.AiOutputSchemaViolationException lastViolation = null;
    for (int attempt = 1; attempt <= MAX_AI_ATTEMPTS; attempt++) {
      String rawJson =
          chatPort.improveFlashcardRaw(
              command.noteType(),
              command.currentContentJson(),
              command.instruction(),
              command.providerConfig());
      try {
        // MANDATORY: validate against schema. The improve prompt returns BARE note content (no
        // envelope, no noteType discriminator), so validate the single note — not the full
        // envelope.
        String validatedJson = schemaValidator.validateNoteAndReturn(command.noteType(), rawJson);
        return new Result(command.noteType(), validatedJson);
      } catch (AiSchemaValidationPort.AiOutputSchemaViolationException ex) {
        lastViolation = ex;
      }
    }
    throw Objects.requireNonNull(
        lastViolation, "retry loop exited without capturing a schema violation");
  }
}
