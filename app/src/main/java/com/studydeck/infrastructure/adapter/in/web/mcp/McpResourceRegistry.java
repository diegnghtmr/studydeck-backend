package com.studydeck.infrastructure.adapter.in.web.mcp;

import com.studydeck.domain.port.in.GetDeckQuery;
import com.studydeck.domain.port.in.ListNoteTypesQuery;
import com.studydeck.infrastructure.adapter.in.web.mcp.dto.McpResourceDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Registry of MCP resources exposed by this server.
 *
 * <p>Resources:
 *
 * <ul>
 *   <li>{@code studydeck://note-types} — list of supported note type descriptors
 *   <li>{@code studydeck://json-schema/current} — the FlashcardImportV1 JSON Schema
 *   <li>{@code studydeck://deck/{id}} — a single deck (read-time, requires auth)
 * </ul>
 *
 * <p>Static resources are pre-resolved at startup; dynamic ones are resolved at read time.
 */
@Component
public class McpResourceRegistry {

  public static final String URI_NOTE_TYPES = "studydeck://note-types";
  public static final String URI_JSON_SCHEMA = "studydeck://json-schema/current";
  public static final String URI_DECK_PREFIX = "studydeck://deck/";

  private static final List<McpResourceDescriptor> STATIC_RESOURCES =
      List.of(
          new McpResourceDescriptor(
              URI_NOTE_TYPES,
              "Note types",
              "List of supported note type descriptors with field schemas",
              "application/json"),
          new McpResourceDescriptor(
              URI_JSON_SCHEMA,
              "FlashcardImportV1 JSON Schema",
              "JSON Schema (Draft 2020-12) for the flashcard import payload",
              "application/schema+json"),
          new McpResourceDescriptor(
              URI_DECK_PREFIX + "{id}",
              "Deck",
              "A single deck owned by the authenticated user (replace {id} with the deck UUID)",
              "application/json"));

  private final ListNoteTypesQuery listNoteTypes;
  private final GetDeckQuery getDeck;
  private final String jsonSchemaContent;

  public McpResourceRegistry(
      @Qualifier("listNoteTypesQuery") ListNoteTypesQuery listNoteTypes,
      @Qualifier("getDeckQuery") GetDeckQuery getDeck) {
    this.listNoteTypes = listNoteTypes;
    this.getDeck = getDeck;
    this.jsonSchemaContent = loadJsonSchema();
  }

  public List<McpResourceDescriptor> list() {
    return STATIC_RESOURCES;
  }

  /**
   * Reads a resource by URI.
   *
   * @param uri the resource URI
   * @param actorId ownerID for resources that require it; may be null for static resources
   * @return the resource content as a map, or empty if the URI is not recognized
   */
  public Optional<Map<String, Object>> read(
      String uri, com.studydeck.domain.model.OwnerId actorId) {
    if (URI_NOTE_TYPES.equals(uri)) {
      return Optional.of(readNoteTypes());
    }
    if (URI_JSON_SCHEMA.equals(uri)) {
      return Optional.of(readJsonSchema());
    }
    if (uri != null && uri.startsWith(URI_DECK_PREFIX)) {
      return Optional.of(readDeck(uri, actorId));
    }
    return Optional.empty();
  }

  // ---------------------------------------------------------------
  // Resource implementations
  // ---------------------------------------------------------------

  private Map<String, Object> readNoteTypes() {
    List<ListNoteTypesQuery.NoteTypeDescriptor> descriptors = listNoteTypes.execute();
    List<Map<String, Object>> types =
        descriptors.stream()
            .map(
                d -> {
                  Map<String, Object> m = new LinkedHashMap<>();
                  m.put("noteType", d.noteType().name().toLowerCase());
                  m.put("label", d.label());
                  m.put("description", d.description());
                  m.put(
                      "fields",
                      d.fields().stream()
                          .map(
                              f -> {
                                Map<String, Object> fm = new LinkedHashMap<>();
                                fm.put("name", f.name());
                                fm.put("required", f.required());
                                if (f.maxLength() > 0) fm.put("maxLength", f.maxLength());
                                fm.put("description", f.description());
                                return fm;
                              })
                          .toList());
                  return m;
                })
            .toList();

    return Map.of(
        "uri",
        URI_NOTE_TYPES,
        "mimeType",
        "application/json",
        "text",
        types.toString(),
        "noteTypes",
        types);
  }

  private Map<String, Object> readJsonSchema() {
    return Map.of(
        "uri", URI_JSON_SCHEMA,
        "mimeType", "application/schema+json",
        "text", jsonSchemaContent);
  }

  private Map<String, Object> readDeck(String uri, com.studydeck.domain.model.OwnerId actorId) {
    if (actorId == null) {
      throw new IllegalArgumentException("actorId required to read deck resource");
    }
    String deckIdStr = uri.substring(URI_DECK_PREFIX.length());
    java.util.UUID deckUuid = java.util.UUID.fromString(deckIdStr);
    com.studydeck.domain.model.DeckId deckId = new com.studydeck.domain.model.DeckId(deckUuid);
    var deck = getDeck.execute(new GetDeckQuery.Query(actorId, deckId));

    Map<String, Object> content = new LinkedHashMap<>();
    content.put("id", deck.getId().value().toString());
    content.put("title", deck.getTitle());
    if (deck.getDescription() != null) content.put("description", deck.getDescription());
    content.put("tags", deck.getTags());
    content.put("archived", deck.isArchived());
    content.put("defaultDesiredRetention", deck.getDefaultDesiredRetention());

    return Map.of(
        "uri", uri,
        "mimeType", "application/json",
        "deck", content);
  }

  private static String loadJsonSchema() {
    try {
      ClassPathResource resource = new ClassPathResource("schemas/flashcard-import-v1.schema.json");
      try (InputStream is = resource.getInputStream()) {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
      }
    } catch (IOException e) {
      return "{}";
    }
  }
}
