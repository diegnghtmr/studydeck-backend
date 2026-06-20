package com.studydeck.application.service;

import com.studydeck.domain.port.in.GenerateFlashcardsUseCase;
import com.studydeck.domain.port.in.ImproveFlashcardUseCase;
import com.studydeck.domain.port.out.AiChatPort;
import com.studydeck.domain.port.out.AiSchemaValidationPort;
import java.util.List;

/**
 * Application service for AI flashcard generation and improvement.
 *
 * <p>ALL AI structured output is validated server-side against the FlashcardImportV1 JSON Schema
 * via {@link AiSchemaValidationPort} before being returned to callers. "Best effort" LLM structured
 * output is not trusted.
 *
 * <p>Generated cards are PROPOSALS — they require explicit human approval before persistence.
 * Auto-persistence is never done here.
 *
 * <p>Framework-free: no Spring annotations. Wired as {@code @Bean} in {@link
 * com.studydeck.infrastructure.config.AiConfiguration}.
 */
public final class AiFlashcardService
    implements GenerateFlashcardsUseCase, ImproveFlashcardUseCase {

  private final AiChatPort chatPort;
  private final AiSchemaValidationPort schemaValidator;

  public AiFlashcardService(AiChatPort chatPort, AiSchemaValidationPort schemaValidator) {
    this.chatPort = chatPort;
    this.schemaValidator = schemaValidator;
  }

  // ---------------------------------------------------------------
  // GenerateFlashcardsUseCase
  // ---------------------------------------------------------------

  @Override
  public GenerateFlashcardsUseCase.Result execute(GenerateFlashcardsUseCase.Command command) {
    if (!chatPort.isAvailable()) {
      throw new AiChatPort.AiChatUnavailableException();
    }

    List<String> noteTypes =
        (command.noteTypes() != null && !command.noteTypes().isEmpty())
            ? command.noteTypes()
            : List.of("basic");

    String rawJson =
        chatPort.generateFlashcardsRaw(
            command.sourceText(),
            command.deckContext(),
            noteTypes,
            Math.max(1, command.maxCards()));

    // MANDATORY: validate the LLM output against FlashcardImportV1 JSON Schema.
    // Throws AiOutputSchemaViolationException if the output is not schema-valid.
    String validatedJson = schemaValidator.validateAndReturn(rawJson);

    return new GenerateFlashcardsUseCase.Result(validatedJson, true);
  }

  // ---------------------------------------------------------------
  // ImproveFlashcardUseCase
  // ---------------------------------------------------------------

  @Override
  public ImproveFlashcardUseCase.Result execute(ImproveFlashcardUseCase.Command command) {
    if (!chatPort.isAvailable()) {
      throw new AiChatPort.AiChatUnavailableException();
    }

    String rawJson =
        chatPort.improveFlashcardRaw(
            command.noteType(), command.currentContentJson(), command.instruction());

    // MANDATORY: validate against schema.
    String validatedJson = schemaValidator.validateAndReturn(rawJson);

    return new ImproveFlashcardUseCase.Result(command.noteType(), validatedJson);
  }
}
