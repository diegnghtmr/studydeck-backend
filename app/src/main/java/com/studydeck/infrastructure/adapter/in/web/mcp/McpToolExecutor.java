package com.studydeck.infrastructure.adapter.in.web.mcp;

import com.studydeck.application.common.PageRequest;
import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.NoteContent;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.ReviewRating;
import com.studydeck.domain.port.in.CreateDeckUseCase;
import com.studydeck.domain.port.in.CreateNoteUseCase;
import com.studydeck.domain.port.in.ExecuteImportUseCase;
import com.studydeck.domain.port.in.ExportDeckUseCase;
import com.studydeck.domain.port.in.GetDeckQuery;
import com.studydeck.domain.port.in.ListDecksQuery;
import com.studydeck.domain.port.in.ListDueCardsQuery;
import com.studydeck.domain.port.in.ListNoteTypesQuery;
import com.studydeck.domain.port.in.SubmitReviewUseCase;
import com.studydeck.domain.port.in.ValidateImportUseCase;
import com.studydeck.domain.port.out.AuditEventPort;
import com.studydeck.infrastructure.adapter.in.web.ImportSchemaValidator;
import com.studydeck.infrastructure.adapter.in.web.mapper.ImportExportMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Executes MCP tool invocations by delegating to the appropriate domain input ports.
 *
 * <p>Security: callers must verify scopes BEFORE calling execute. This class does not enforce
 * scopes — it trusts the controller layer to have done so. It DOES emit an audit event for every
 * invocation (success or failure).
 *
 * <p>Tool → scope mapping (enforced by {@link McpManagementController} and the security config):
 *
 * <ul>
 *   <li>{@code deck_list} → {@code mcp.invoke + study.read}
 *   <li>{@code deck_create} → {@code mcp.invoke + study.write}
 *   <li>{@code note_create} → {@code mcp.invoke + study.write}
 *   <li>{@code import_json} → {@code mcp.invoke + import.write}
 *   <li>{@code export_deck} → {@code mcp.invoke + export.read}
 *   <li>{@code study_get_queue} → {@code mcp.invoke + study.read}
 *   <li>{@code review_submit} → {@code mcp.invoke + review.write}
 *   <li>{@code capabilities_get} → {@code mcp.invoke}
 * </ul>
 */
@Component
public class McpToolExecutor {

  // -- Tool names --
  public static final String TOOL_DECK_LIST = "deck_list";
  public static final String TOOL_DECK_CREATE = "deck_create";
  public static final String TOOL_NOTE_CREATE = "note_create";
  public static final String TOOL_IMPORT_JSON = "import_json";
  public static final String TOOL_EXPORT_DECK = "export_deck";
  public static final String TOOL_STUDY_GET_QUEUE = "study_get_queue";
  public static final String TOOL_REVIEW_SUBMIT = "review_submit";
  public static final String TOOL_CAPABILITIES_GET = "capabilities_get";

  /** Maps tool name → required secondary scope (beyond mcp.invoke). */
  public static final Map<String, String> TOOL_SCOPE =
      Map.of(
          TOOL_DECK_LIST, "study.read",
          TOOL_DECK_CREATE, "study.write",
          TOOL_NOTE_CREATE, "study.write",
          TOOL_IMPORT_JSON, "import.write",
          TOOL_EXPORT_DECK, "export.read",
          TOOL_STUDY_GET_QUEUE, "study.read",
          TOOL_REVIEW_SUBMIT, "review.write",
          TOOL_CAPABILITIES_GET, "" // no secondary scope
          );

  private final ListDecksQuery listDecks;
  private final CreateDeckUseCase createDeck;
  private final GetDeckQuery getDeck;
  private final CreateNoteUseCase createNote;
  private final ExecuteImportUseCase executeImport;
  private final ValidateImportUseCase validateImport;
  private final ExportDeckUseCase exportDeck;
  private final ListDueCardsQuery listDueCards;
  private final SubmitReviewUseCase submitReview;
  private final ListNoteTypesQuery listNoteTypes;
  private final AuditEventPort auditEventPort;
  private final ImportSchemaValidator schemaValidator;
  private final ImportExportMapper importExportMapper;
  private final ObjectMapper objectMapper;

  @SuppressWarnings("checkstyle:ParameterNumber")
  public McpToolExecutor(
      @Qualifier("listDecksQuery") ListDecksQuery listDecks,
      @Qualifier("createDeckUseCase") CreateDeckUseCase createDeck,
      @Qualifier("getDeckQuery") GetDeckQuery getDeck,
      @Qualifier("createNoteUseCase") CreateNoteUseCase createNote,
      @Qualifier("executeImportUseCase") ExecuteImportUseCase executeImport,
      @Qualifier("validateImportUseCase") ValidateImportUseCase validateImport,
      @Qualifier("exportDeckUseCase") ExportDeckUseCase exportDeck,
      @Qualifier("listDueCardsQuery") ListDueCardsQuery listDueCards,
      @Qualifier("submitReviewUseCase") SubmitReviewUseCase submitReview,
      @Qualifier("listNoteTypesQuery") ListNoteTypesQuery listNoteTypes,
      AuditEventPort auditEventPort,
      ImportSchemaValidator schemaValidator,
      ImportExportMapper importExportMapper,
      ObjectMapper objectMapper) {
    this.listDecks = listDecks;
    this.createDeck = createDeck;
    this.getDeck = getDeck;
    this.createNote = createNote;
    this.executeImport = executeImport;
    this.validateImport = validateImport;
    this.exportDeck = exportDeck;
    this.listDueCards = listDueCards;
    this.submitReview = submitReview;
    this.listNoteTypes = listNoteTypes;
    this.auditEventPort = auditEventPort;
    this.schemaValidator = schemaValidator;
    this.importExportMapper = importExportMapper;
    this.objectMapper = objectMapper;
  }

  /**
   * Execute the named tool with the given arguments on behalf of the given actor.
   *
   * @param toolName the tool identifier (one of the TOOL_* constants)
   * @param args the arguments map (already parsed from JSON)
   * @param actorId the authenticated user
   * @return execution result map (to be serialized as McpInvokeResponse.content)
   * @throws McpToolException if the tool name is unknown or execution fails
   */
  public Map<String, Object> execute(String toolName, Map<String, Object> args, OwnerId actorId) {
    Map<String, Object> result;
    String outcome = "success";
    try {
      result =
          switch (toolName) {
            case TOOL_DECK_LIST -> executeDeckList(args, actorId);
            case TOOL_DECK_CREATE -> executeDeckCreate(args, actorId);
            case TOOL_NOTE_CREATE -> executeNoteCreate(args, actorId);
            case TOOL_IMPORT_JSON -> executeImportJson(args, actorId);
            case TOOL_EXPORT_DECK -> executeExportDeck(args, actorId);
            case TOOL_STUDY_GET_QUEUE -> executeStudyGetQueue(args, actorId);
            case TOOL_REVIEW_SUBMIT -> executeReviewSubmit(args, actorId);
            case TOOL_CAPABILITIES_GET -> executeCapabilitiesGet();
            default -> throw new McpToolException("unknown_tool", "Unknown tool: " + toolName);
          };
    } catch (McpToolException mte) {
      outcome = "error";
      auditEventPort.record(actorId, "mcp.tool." + toolName, "McpTool", outcome);
      throw mte;
    } catch (Exception e) {
      outcome = "error";
      auditEventPort.record(actorId, "mcp.tool." + toolName, "McpTool", outcome);
      throw new McpToolException("execution_error", e.getMessage(), e);
    }
    auditEventPort.record(actorId, "mcp.tool." + toolName, "McpTool", outcome);
    return result;
  }

  // ---------------------------------------------------------------
  // Tool implementations
  // ---------------------------------------------------------------

  private Map<String, Object> executeDeckList(Map<String, Object> args, OwnerId actorId) {
    String search = stringArg(args, "search", null);
    int page = intArg(args, "page", 0);
    int size = intArg(args, "size", 20);
    boolean includeArchived = boolArg(args, "includeArchived", false);

    var result =
        listDecks.execute(
            new ListDecksQuery.Query(actorId, includeArchived, search, PageRequest.of(page, size)));

    List<Map<String, Object>> decks =
        result.content().stream()
            .map(
                d -> {
                  Map<String, Object> m = new LinkedHashMap<>();
                  m.put("id", d.getId().value().toString());
                  m.put("title", d.getTitle());
                  if (d.getDescription() != null) m.put("description", d.getDescription());
                  m.put("archived", d.isArchived());
                  m.put("tags", d.getTags());
                  return m;
                })
            .toList();

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("decks", decks);
    out.put("totalElements", result.totalElements());
    out.put("totalPages", result.totalPages());
    out.put("page", result.page());
    out.put("size", result.size());
    return out;
  }

  private Map<String, Object> executeDeckCreate(Map<String, Object> args, OwnerId actorId) {
    String title = requiredString(args, "title");
    String description = stringArg(args, "description", null);
    @SuppressWarnings("unchecked")
    List<String> tags = (List<String>) args.get("tags");
    double retention = doubleArg(args, "defaultDesiredRetention", 0.9);

    DeckId deckId =
        createDeck.execute(
            new CreateDeckUseCase.Command(actorId, title, description, tags, retention));

    var deck = getDeck.execute(new GetDeckQuery.Query(actorId, deckId));

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", deck.getId().value().toString());
    out.put("title", deck.getTitle());
    if (deck.getDescription() != null) out.put("description", deck.getDescription());
    out.put("tags", deck.getTags());
    out.put("defaultDesiredRetention", deck.getDefaultDesiredRetention());
    return out;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> executeNoteCreate(Map<String, Object> args, OwnerId actorId) {
    String deckIdStr = requiredString(args, "deckId");
    String noteTypeStr = requiredString(args, "noteType");
    List<String> tags = (List<String>) args.get("tags");

    DeckId deckId = new DeckId(UUID.fromString(deckIdStr));
    NoteContent content = buildNoteContent(noteTypeStr, args);

    CreateNoteUseCase.Result result =
        createNote.execute(new CreateNoteUseCase.Command(actorId, deckId, content, tags));

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("noteId", result.noteId().value().toString());
    out.put("cardIds", result.cardIds().stream().map(c -> c.value().toString()).toList());
    return out;
  }

  private Map<String, Object> executeImportJson(Map<String, Object> args, OwnerId actorId) {
    // Convert args map back to JsonNode for schema validation + mapper
    JsonNode body = objectMapper.valueToTree(args);

    List<String> schemaErrors = schemaValidator.validate(body);
    if (!schemaErrors.isEmpty()) {
      throw new McpToolException("schema_validation_error", "Schema errors: " + schemaErrors);
    }

    ValidateImportUseCase.ImportPayload payload = importExportMapper.toPayload(body);

    // Domain validation
    ValidateImportUseCase.Result validation =
        validateImport.execute(new ValidateImportUseCase.Command(actorId, payload));
    if (!validation.valid()) {
      throw new McpToolException(
          "domain_validation_error", "Domain errors: " + validation.errors());
    }

    String targetDeckIdStr = stringArg(args, "targetDeckId", null);
    DeckId targetDeckId =
        targetDeckIdStr != null ? new DeckId(UUID.fromString(targetDeckIdStr)) : null;

    ExecuteImportUseCase.ImportResult result =
        executeImport.execute(new ExecuteImportUseCase.Command(actorId, payload, targetDeckId));

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("importId", result.importId().toString());
    out.put("deckId", result.deckId().toString());
    out.put("importedNotes", result.importedNotes());
    out.put("importedCards", result.importedCards());
    out.put("duplicateNotes", result.duplicateNotes());
    out.put("rejectedNotes", result.rejectedNotes());
    if (!result.warnings().isEmpty()) out.put("warnings", result.warnings());
    return out;
  }

  private Map<String, Object> executeExportDeck(Map<String, Object> args, OwnerId actorId) {
    String deckIdStr = requiredString(args, "deckId");
    DeckId deckId = new DeckId(UUID.fromString(deckIdStr));

    ValidateImportUseCase.ImportPayload exported =
        exportDeck.execute(new ExportDeckUseCase.Command(actorId, deckId));

    JsonNode node = importExportMapper.toJson(exported, objectMapper);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("export", objectMapper.convertValue(node, Map.class));
    return out;
  }

  private Map<String, Object> executeStudyGetQueue(Map<String, Object> args, OwnerId actorId) {
    String deckIdStr = stringArg(args, "deckId", null);
    int limit = intArg(args, "limit", 20);

    DeckId deckId = deckIdStr != null ? new DeckId(UUID.fromString(deckIdStr)) : null;

    var cards = listDueCards.execute(new ListDueCardsQuery.Query(actorId, deckId, limit));

    List<Map<String, Object>> cardList =
        cards.stream()
            .map(
                c -> {
                  Map<String, Object> m = new LinkedHashMap<>();
                  m.put("id", c.getId().value().toString());
                  m.put("noteId", c.getNoteId().value().toString());
                  m.put("noteType", c.getNoteType().name().toLowerCase());
                  m.put("ordinal", c.getOrdinal());
                  return m;
                })
            .toList();

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("cards", cardList);
    out.put("count", cardList.size());
    return out;
  }

  private Map<String, Object> executeReviewSubmit(Map<String, Object> args, OwnerId actorId) {
    String cardIdStr = requiredString(args, "cardId");
    String ratingStr = requiredString(args, "rating");
    String sessionIdStr = stringArg(args, "sessionId", null);
    Integer responseTimeMs = intArgNullable(args, "responseTimeMs");

    CardId cardId = new CardId(UUID.fromString(cardIdStr));
    ReviewRating rating = ReviewRating.valueOf(ratingStr.toUpperCase());
    UUID sessionId = sessionIdStr != null ? UUID.fromString(sessionIdStr) : null;

    SubmitReviewUseCase.Result result =
        submitReview.execute(
            new SubmitReviewUseCase.Command(actorId, cardId, rating, sessionId, responseTimeMs));

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("historyEntryId", result.historyEntryId().toString());

    var rr = result.reviewResult();
    Map<String, Object> nextState = new LinkedHashMap<>();
    nextState.put("scheduledDays", rr.nextState().scheduledDays());
    nextState.put("dueAt", rr.nextState().dueAt().toString());
    nextState.put("state", rr.nextState().state().name());
    out.put("nextState", nextState);
    return out;
  }

  private Map<String, Object> executeCapabilitiesGet() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("serverName", "studydeck-mcp");
    out.put("serverVersion", "1.0");
    out.put("protocolVersion", "2024-11-05");
    out.put(
        "capabilities",
        Map.of(
            "tools", true,
            "resources", true,
            "prompts", false,
            "logging", false));
    out.put(
        "tools",
        List.of(
            TOOL_DECK_LIST,
            TOOL_DECK_CREATE,
            TOOL_NOTE_CREATE,
            TOOL_IMPORT_JSON,
            TOOL_EXPORT_DECK,
            TOOL_STUDY_GET_QUEUE,
            TOOL_REVIEW_SUBMIT,
            TOOL_CAPABILITIES_GET));
    return out;
  }

  // ---------------------------------------------------------------
  // NoteContent builder
  // ---------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private NoteContent buildNoteContent(String noteTypeStr, Map<String, Object> args) {
    return switch (noteTypeStr.toLowerCase()) {
      case "basic" ->
          new NoteContent.Basic(requiredString(args, "front"), requiredString(args, "back"));
      case "reversed" ->
          new NoteContent.Reversed(requiredString(args, "front"), requiredString(args, "back"));
      case "cloze" -> new NoteContent.Cloze(requiredString(args, "text"));
      case "mcq", "multiple_choice" -> {
        String question = requiredString(args, "question");
        List<Map<String, Object>> rawOptions = (List<Map<String, Object>>) args.get("options");
        if (rawOptions == null || rawOptions.isEmpty()) {
          throw new McpToolException("missing_argument", "options is required for MCQ notes");
        }
        List<NoteContent.MultipleChoice.Option> options =
            rawOptions.stream()
                .map(o -> new NoteContent.MultipleChoice.Option(str(o, "key"), str(o, "text")))
                .toList();
        List<String> correct = (List<String>) args.get("correctOptionKeys");
        if (correct == null || correct.isEmpty()) {
          throw new McpToolException(
              "missing_argument", "correctOptionKeys is required for MCQ notes");
        }
        String explanation = stringArg(args, "explanation", null);
        yield new NoteContent.MultipleChoice(question, options, correct, explanation);
      }
      case "frq", "free_text" ->
          new NoteContent.FreeText(
              requiredString(args, "question"),
              requiredString(args, "expectedAnswer"),
              stringArg(args, "gradingGuidance", null));
      case "typed" ->
          new NoteContent.FreeText(
              requiredString(args, "front"), requiredString(args, "expectedAnswer"), null);
      default -> throw new McpToolException("invalid_argument", "Unknown noteType: " + noteTypeStr);
    };
  }

  // ---------------------------------------------------------------
  // Argument helpers
  // ---------------------------------------------------------------

  private String requiredString(Map<String, Object> args, String key) {
    Object v = args == null ? null : args.get(key);
    if (v == null || v.toString().isBlank()) {
      throw new McpToolException("missing_argument", "Required argument missing: " + key);
    }
    return v.toString();
  }

  private String stringArg(Map<String, Object> args, String key, String defaultValue) {
    Object v = args == null ? null : args.get(key);
    return v != null ? v.toString() : defaultValue;
  }

  private int intArg(Map<String, Object> args, String key, int defaultValue) {
    Object v = args == null ? null : args.get(key);
    if (v == null) return defaultValue;
    return ((Number) v).intValue();
  }

  private Integer intArgNullable(Map<String, Object> args, String key) {
    Object v = args == null ? null : args.get(key);
    return v != null ? ((Number) v).intValue() : null;
  }

  private double doubleArg(Map<String, Object> args, String key, double defaultValue) {
    Object v = args == null ? null : args.get(key);
    if (v == null) return defaultValue;
    return ((Number) v).doubleValue();
  }

  private boolean boolArg(Map<String, Object> args, String key, boolean defaultValue) {
    Object v = args == null ? null : args.get(key);
    if (v == null) return defaultValue;
    return Boolean.parseBoolean(v.toString());
  }

  private String str(Map<String, Object> map, String key) {
    Object v = map.get(key);
    if (v == null)
      throw new McpToolException("invalid_argument", "Missing key '" + key + "' in map");
    return v.toString();
  }

  // ---------------------------------------------------------------
  // Inner exception
  // ---------------------------------------------------------------

  /** Signals a tool-level error with an MCP error code. */
  public static class McpToolException extends RuntimeException {
    private final String code;

    public McpToolException(String code, String message) {
      super(message);
      this.code = code;
    }

    public McpToolException(String code, String message, Throwable cause) {
      super(message, cause);
      this.code = code;
    }

    public String getCode() {
      return code;
    }
  }
}
