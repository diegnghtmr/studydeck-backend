package com.studydeck.application.service;

import com.studydeck.domain.port.in.GenerateFlashcardsUseCase;
import com.studydeck.domain.port.out.AiChatPort;
import com.studydeck.domain.port.out.AiSchemaValidationPort;
import java.util.List;

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

  private final AiChatPort chatPort;
  private final AiSchemaValidationPort schemaValidator;

  public GenerateFlashcardsService(AiChatPort chatPort, AiSchemaValidationPort schemaValidator) {
    this.chatPort = chatPort;
    this.schemaValidator = schemaValidator;
  }

  @Override
  public Result execute(Command command) {
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
    String validatedJson = schemaValidator.validateAndReturn(rawJson);

    return new Result(validatedJson, true);
  }
}
