package com.studydeck.domain.port.out;

import com.studydeck.domain.model.AiProviderConfig;
import com.studydeck.domain.model.OwnerId;
import java.util.List;

/**
 * Output port for AI chat operations (RAG chat + flashcard generation/improvement).
 *
 * <p>Implemented by the Spring AI adapter. The domain never imports Spring AI types.
 *
 * <p>If no chat provider is configured, the adapter throws {@link AiChatUnavailableException} which
 * the application layer translates to a 503 ProblemDetail response.
 */
public interface AiChatPort {

  /**
   * Returns true if a chat provider is configured and available.
   *
   * <p>The application layer checks this before invoking chat operations and returns a graceful 503
   * if false.
   */
  boolean isAvailable();

  /**
   * Generates a RAG-grounded answer for the user's question, using context chunks retrieved from
   * the vector store.
   *
   * @param question the user's question
   * @param ownerId owner scope for RAG retrieval
   * @param contextChunks pre-retrieved relevant chunks (already filtered by ownerId)
   * @param override per-request AI provider configuration (BYOK); {@code null} = use global
   * @return the answer text and cited source chunk IDs
   */
  RagAnswer ragChat(
      String question,
      OwnerId ownerId,
      List<ContextChunk> contextChunks,
      AiProviderConfig override);

  /**
   * Generates flashcard proposals from the given source text.
   *
   * @param sourceText the content to generate cards from
   * @param deckContext optional context about the target deck (title, topic)
   * @param noteTypes desired note types (e.g. ["basic", "cloze"])
   * @param maxCards maximum number of cards to generate
   * @param override per-request AI provider configuration (BYOK); {@code null} = use global
   * @return raw structured output JSON — caller MUST validate against FlashcardImportV1 schema
   */
  String generateFlashcardsRaw(
      String sourceText,
      String deckContext,
      List<String> noteTypes,
      int maxCards,
      AiProviderConfig override);

  /**
   * Improves an existing flashcard.
   *
   * @param noteType the note type (basic, cloze, etc.)
   * @param currentContent JSON representation of the current card content
   * @param instruction improvement instruction from the user
   * @return raw improved card content JSON — caller MUST validate against FlashcardImportV1 schema
   */
  String improveFlashcardRaw(String noteType, String currentContent, String instruction);

  // ---------------------------------------------------------------
  // Value types
  // ---------------------------------------------------------------

  record ContextChunk(String chunkId, String documentId, String content) {}

  record RagAnswer(String answer, List<CitedChunk> citations) {}

  record CitedChunk(String chunkId, String documentId, double score, String content) {}

  /** Thrown when no chat provider is configured. Application layer maps this to 503. */
  class AiChatUnavailableException extends RuntimeException {
    public AiChatUnavailableException() {
      super(
          "AI chat provider is not configured. Set SPRING_AI_OLLAMA_BASE_URL or SPRING_AI_OPENAI_API_KEY.");
    }
  }
}
