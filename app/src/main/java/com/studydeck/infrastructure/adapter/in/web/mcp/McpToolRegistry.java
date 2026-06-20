package com.studydeck.infrastructure.adapter.in.web.mcp;

import com.studydeck.infrastructure.adapter.in.web.mcp.dto.McpToolDescriptor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Registry of all MCP tools exposed by this server.
 *
 * <p>Tools are pure metadata (name, description, JSON input schema). Execution is delegated to
 * {@link McpToolExecutor} which calls the appropriate domain input port.
 *
 * <p>Tool catalogue:
 *
 * <ul>
 *   <li>{@code deck_list} — list decks owned by the authenticated user
 *   <li>{@code deck_create} — create a new deck
 *   <li>{@code note_create} — create a note in a deck
 *   <li>{@code import_json} — import a FlashcardImportV1 JSON payload
 *   <li>{@code export_deck} — export a deck as FlashcardImportV1 JSON
 *   <li>{@code study_get_queue} — list due cards (optionally filtered by deck)
 *   <li>{@code review_submit} — submit a review rating for a card
 *   <li>{@code capabilities_get} — return server capabilities and version
 * </ul>
 */
@Component
public class McpToolRegistry {

  private final Map<String, McpToolDescriptor> tools;

  public McpToolRegistry() {
    this.tools = new LinkedHashMap<>();
    registerAll();
  }

  public List<McpToolDescriptor> all() {
    return List.copyOf(tools.values());
  }

  public Optional<McpToolDescriptor> find(String name) {
    return Optional.ofNullable(tools.get(name));
  }

  // ---------------------------------------------------------------
  // Registration
  // ---------------------------------------------------------------

  private void registerAll() {
    register(
        "deck_list",
        "List decks",
        "List all decks owned by the authenticated user. Supports optional search and pagination.",
        Map.of(
            "type",
            "object",
            "properties",
            Map.of(
                "search",
                Map.of("type", "string", "description", "Optional substring filter on title"),
                "page",
                Map.of(
                    "type",
                    "integer",
                    "description",
                    "Zero-based page index (default 0)",
                    "minimum",
                    0),
                "size",
                Map.of(
                    "type",
                    "integer",
                    "description",
                    "Page size (default 20, max 100)",
                    "minimum",
                    1,
                    "maximum",
                    100),
                "includeArchived",
                Map.of(
                    "type",
                    "boolean",
                    "description",
                    "When true, include archived decks (default false)")),
            "additionalProperties",
            false));

    register(
        "deck_create",
        "Create deck",
        "Create a new study deck owned by the authenticated user. Requires study.write scope.",
        Map.of(
            "type",
            "object",
            "required",
            List.of("title"),
            "properties",
            Map.of(
                "title",
                Map.of(
                    "type",
                    "string",
                    "description",
                    "Deck title (1-120 characters)",
                    "minLength",
                    1,
                    "maxLength",
                    120),
                "description",
                Map.of(
                    "type",
                    "string",
                    "description",
                    "Optional description (max 1000 characters)",
                    "maxLength",
                    1000),
                "tags",
                Map.of(
                    "type",
                    "array",
                    "items",
                    Map.of("type", "string"),
                    "description",
                    "Optional tag list"),
                "defaultDesiredRetention",
                Map.of(
                    "type",
                    "number",
                    "description",
                    "Target retention rate 0.70-0.99 (default 0.9)",
                    "minimum",
                    0.70,
                    "maximum",
                    0.99)),
            "additionalProperties",
            false));

    register(
        "note_create",
        "Create note",
        "Create a note in a deck. The note type determines which fields are required."
            + " Requires study.write scope.",
        Map.of(
            "type",
            "object",
            "required",
            List.of("deckId", "noteType"),
            "properties",
            Map.of(
                "deckId",
                Map.of(
                    "type", "string",
                    "format", "uuid",
                    "description", "Target deck UUID"),
                "noteType",
                Map.of(
                    "type", "string",
                    "enum", List.of("basic", "cloze", "mcq", "frq", "typed"),
                    "description", "Note type"),
                "front",
                Map.of(
                    "type", "string",
                    "description", "Front side (basic/typed)"),
                "back",
                Map.of(
                    "type", "string",
                    "description", "Back side (basic)"),
                "text",
                Map.of(
                    "type", "string",
                    "description", "Text with {{cloze}} markers (cloze)"),
                "question",
                Map.of(
                    "type", "string",
                    "description", "Question text (mcq/frq)"),
                "options",
                Map.of(
                    "type",
                    "array",
                    "description",
                    "Answer options (mcq)",
                    "items",
                    Map.of(
                        "type",
                        "object",
                        "required",
                        List.of("key", "text"),
                        "properties",
                        Map.of(
                            "key", Map.of("type", "string"),
                            "text", Map.of("type", "string")))),
                "correctOptionKeys",
                Map.of(
                    "type", "array",
                    "items", Map.of("type", "string"),
                    "description", "Keys of correct options (mcq)"),
                "expectedAnswer",
                Map.of(
                    "type", "string",
                    "description", "Expected answer (typed/frq)"),
                "tags",
                Map.of(
                    "type", "array",
                    "items", Map.of("type", "string"),
                    "description", "Optional tag list")),
            "additionalProperties",
            false));

    register(
        "import_json",
        "Import flashcards",
        "Import a FlashcardImportV1 JSON payload into a deck. Requires import.write scope.",
        Map.of(
            "type",
            "object",
            "required",
            List.of("schemaVersion", "deck", "notes"),
            "properties",
            Map.of(
                "schemaVersion",
                Map.of(
                    "type", "string",
                    "const", "1.0",
                    "description", "Must be '1.0'"),
                "deck",
                Map.of(
                    "type",
                    "object",
                    "required",
                    List.of("title"),
                    "properties",
                    Map.of(
                        "title",
                        Map.of("type", "string"),
                        "description",
                        Map.of("type", "string"),
                        "tags",
                        Map.of("type", "array", "items", Map.of("type", "string")))),
                "notes",
                Map.of(
                    "type",
                    "array",
                    "description",
                    "List of notes to import",
                    "items",
                    Map.of("type", "object")),
                "targetDeckId",
                Map.of(
                    "type",
                    "string",
                    "format",
                    "uuid",
                    "description",
                    "Optional target deck UUID. If null, a new deck is created.")),
            "additionalProperties",
            false));

    register(
        "export_deck",
        "Export deck",
        "Export a deck as FlashcardImportV1 JSON. Requires export.read scope.",
        Map.of(
            "type",
            "object",
            "required",
            List.of("deckId"),
            "properties",
            Map.of(
                "deckId",
                Map.of(
                    "type", "string",
                    "format", "uuid",
                    "description", "UUID of the deck to export")),
            "additionalProperties",
            false));

    register(
        "study_get_queue",
        "Get study queue",
        "Return due cards for review, optionally filtered by deck. Requires study.read scope.",
        Map.of(
            "type",
            "object",
            "properties",
            Map.of(
                "deckId",
                Map.of(
                    "type", "string",
                    "format", "uuid",
                    "description", "Optional deck UUID filter"),
                "limit",
                Map.of(
                    "type",
                    "integer",
                    "description",
                    "Max cards to return (1-200, default 20)",
                    "minimum",
                    1,
                    "maximum",
                    200)),
            "additionalProperties",
            false));

    register(
        "review_submit",
        "Submit review",
        "Submit a review rating (again/hard/good/easy) for a card. Requires review.write scope.",
        Map.of(
            "type",
            "object",
            "required",
            List.of("cardId", "rating"),
            "properties",
            Map.of(
                "cardId",
                Map.of(
                    "type", "string",
                    "format", "uuid",
                    "description", "UUID of the card being reviewed"),
                "rating",
                Map.of(
                    "type", "string",
                    "enum", List.of("again", "hard", "good", "easy"),
                    "description", "Review rating"),
                "sessionId",
                Map.of(
                    "type", "string",
                    "format", "uuid",
                    "description", "Optional review session UUID"),
                "responseTimeMs",
                Map.of(
                    "type", "integer",
                    "description", "Optional response time in milliseconds",
                    "minimum", 0)),
            "additionalProperties",
            false));

    register(
        "capabilities_get",
        "Get capabilities",
        "Return server capabilities, available tools, and API version. No scopes required beyond"
            + " mcp.invoke.",
        Map.of("type", "object", "properties", Map.of(), "additionalProperties", false));
  }

  private void register(String name, String title, String description, Map<String, Object> schema) {
    tools.put(name, new McpToolDescriptor(name, title, description, schema));
  }
}
