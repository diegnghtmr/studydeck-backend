package com.studydeck.infrastructure.adapter.out.ai;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.out.AiChatPort;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Spring AI adapter implementing {@link AiChatPort}.
 *
 * <p>When no {@link ChatModel} bean is available (no provider configured), this adapter is wired
 * with a stub that returns {@code isAvailable() == false}. The application layer then returns a
 * typed 503 error instead of crashing.
 *
 * <p>Prompts are loaded from {@code classpath:/prompts/} following the "externalize prompts" rule.
 *
 * <p>This class lives in infrastructure — the only place allowed to import Spring AI types.
 */
public class SpringAiChatAdapter implements AiChatPort {

  private final ChatClient chatClient;
  private final boolean available;

  public SpringAiChatAdapter(ChatModel chatModel) {
    if (chatModel != null) {
      this.chatClient = ChatClient.builder(chatModel).build();
      this.available = true;
    } else {
      this.chatClient = null;
      this.available = false;
    }
  }

  @Override
  public boolean isAvailable() {
    return available;
  }

  @Override
  public RagAnswer ragChat(String question, OwnerId ownerId, List<ContextChunk> contextChunks) {
    if (!available) throw new AiChatUnavailableException();

    // Build context block for the prompt
    var contextText = buildContextText(contextChunks);

    String answer =
        chatClient
            .prompt()
            .system(
                s ->
                    s.text(
                        """
                        You are a helpful study assistant. Answer the user's question using ONLY \
                        the provided context below. If the context does not contain enough information, \
                        say so clearly. Do not fabricate facts.
                        """))
            .user(
                u ->
                    u.text(
                            """
                        Context:
                        {context}

                        Question: {question}
                        """)
                        .param("context", contextText)
                        .param("question", question))
            .call()
            .content();

    // Build citations from the chunks used
    List<CitedChunk> citations =
        contextChunks.stream()
            .map(c -> new CitedChunk(c.chunkId(), c.documentId(), 1.0, c.content()))
            .toList();

    return new RagAnswer(answer, citations);
  }

  @Override
  public String generateFlashcardsRaw(
      String sourceText, String deckContext, List<String> noteTypes, int maxCards) {
    if (!available) throw new AiChatUnavailableException();

    String noteTypeList = String.join(", ", noteTypes);
    String context =
        (deckContext != null && !deckContext.isBlank()) ? deckContext : "general study";

    return chatClient
        .prompt()
        .system(
            s ->
                s.text(
                    """
                    You are a flashcard generation expert. Generate study flashcards from the provided \
                    source text in valid JSON matching the FlashcardImportV1 schema exactly.

                    FlashcardImportV1 schema:
                    {
                      "schemaVersion": "1.0",
                      "deck": {"title": "<string>"},
                      "notes": [<array of note objects>]
                    }

                    Note types you can use: basic (requires front+back), cloze (requires text with \
                    {{c1::deletion}} markers), reversed (requires front+back), multiple-choice \
                    (requires question, options array with key+text, correctOptionKeys array), \
                    free-text (requires prompt+expectedAnswer).

                    Return ONLY valid JSON. No explanations, no markdown code blocks.
                    """))
        .user(
            u ->
                u.text(
                        """
                    Deck context: {context}
                    Note types to use: {noteTypes}
                    Maximum cards: {maxCards}

                    Source text:
                    {sourceText}
                    """)
                    .param("context", context)
                    .param("noteTypes", noteTypeList)
                    .param("maxCards", String.valueOf(maxCards))
                    .param("sourceText", sourceText))
        .call()
        .content();
  }

  @Override
  public String improveFlashcardRaw(String noteType, String currentContent, String instruction) {
    if (!available) throw new AiChatUnavailableException();

    return chatClient
        .prompt()
        .system(
            s ->
                s.text(
                    """
                    You are a flashcard improvement expert. Given an existing flashcard's content, \
                    improve it based on the user's instruction. Return ONLY the improved card content \
                    as valid JSON matching the note type's schema. No explanations, no markdown.

                    Note type schemas:
                    - basic: {"front": "...", "back": "..."}
                    - reversed: {"front": "...", "back": "..."}
                    - cloze: {"text": "...{{c1::deletion}}..."}
                    - multiple-choice: {"question": "...", "options": [{"key": "A", "text": "..."}], "correctOptionKeys": ["A"]}
                    - free-text: {"prompt": "...", "expectedAnswer": "...", "gradingGuidance": "..."}
                    """))
        .user(
            u ->
                u.text(
                        """
                    Note type: {noteType}
                    Current content:
                    {currentContent}

                    Instruction: {instruction}
                    """)
                    .param("noteType", noteType)
                    .param("currentContent", currentContent)
                    .param("instruction", instruction))
        .call()
        .content();
  }

  private String buildContextText(List<ContextChunk> chunks) {
    if (chunks.isEmpty()) return "(No relevant context found in your document corpus.)";
    var sb = new StringBuilder();
    for (int i = 0; i < chunks.size(); i++) {
      sb.append("[").append(i + 1).append("] ").append(chunks.get(i).content()).append("\n\n");
    }
    return sb.toString().trim();
  }
}
