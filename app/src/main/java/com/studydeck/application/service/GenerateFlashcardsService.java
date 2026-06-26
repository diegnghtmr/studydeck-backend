package com.studydeck.application.service;

import com.studydeck.domain.port.in.GenerateFlashcardsUseCase;
import com.studydeck.domain.port.out.AiChatPort;
import com.studydeck.domain.port.out.AiSchemaValidationPort;
import java.util.List;
import java.util.Objects;

/**
 * Application service implementing the GenerateFlashcards use case.
 *
 * <p>ALL AI structured output is validated server-side against the FlashcardImportV1 JSON Schema
 * via {@link AiSchemaValidationPort} before being returned. Generated cards are PROPOSALS — they
 * require explicit human approval before persistence. Auto-persistence is never done here.
 *
 * <p>Framework-free: no Spring annotations.
 */
public final class GenerateFlashcardsService implements GenerateFlashcardsUseCase {

  /**
   * Total attempts (1 initial + retries) when the model output violates the schema. Reasoning
   * models are non-deterministic, so re-asking usually yields conformant JSON on a later try.
   */
  private static final int MAX_AI_ATTEMPTS = 3;

  private final AiChatPort chatPort;
  private final AiSchemaValidationPort schemaValidator;

  public GenerateFlashcardsService(AiChatPort chatPort, AiSchemaValidationPort schemaValidator) {
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

    List<String> noteTypes =
        (command.noteTypes() != null && !command.noteTypes().isEmpty())
            ? command.noteTypes()
            : List.of("basic");

    // Re-ask the model on schema violations only (non-deterministic reasoning output). Any other
    // failure (e.g. AiChatUnavailableException) propagates immediately and is never retried.
    AiSchemaValidationPort.AiOutputSchemaViolationException lastViolation = null;
    for (int attempt = 1; attempt <= MAX_AI_ATTEMPTS; attempt++) {
      String rawJson =
          chatPort.generateFlashcardsRaw(
              command.sourceText(),
              command.deckContext(),
              noteTypes,
              Math.max(1, command.maxCards()),
              command.providerConfig());
      try {
        // MANDATORY: validate the LLM output against FlashcardImportV1 JSON Schema.
        String validatedJson = schemaValidator.validateAndReturn(rawJson);
        return new Result(validatedJson, true);
      } catch (AiSchemaValidationPort.AiOutputSchemaViolationException ex) {
        lastViolation = ex;
      }
    }
    throw Objects.requireNonNull(
        lastViolation, "retry loop exited without capturing a schema violation");
  }
}
