package com.studydeck.infrastructure.adapter.in.web.mapper;

import com.studydeck.domain.port.in.ExecuteImportUseCase;
import com.studydeck.domain.port.in.PreviewImportUseCase;
import com.studydeck.domain.port.in.ValidateImportUseCase;
import com.studydeck.domain.port.in.ValidateImportUseCase.ImportPayload;
import com.studydeck.domain.port.in.ValidateImportUseCase.ImportPayload.DeckMeta;
import com.studydeck.domain.port.in.ValidateImportUseCase.ImportPayload.NoteImport;
import com.studydeck.domain.port.in.ValidateImportUseCase.ImportPayload.OptionImport;
import com.studydeck.domain.port.in.ValidateImportUseCase.ImportPayload.SourceRef;
import com.studydeck.infrastructure.adapter.in.web.dto.ImportPreviewResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.ImportResultResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.ImportValidationResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeType;

/**
 * Mapper between JSON nodes (raw request body) and domain ImportPayload, and between domain results
 * and web response DTOs.
 */
@Component
public class ImportExportMapper {

  // ---------------------------------------------------------------
  // JSON → Domain
  // ---------------------------------------------------------------

  /**
   * Parses the raw JSON request body (already deserialized by Jackson) into a domain {@link
   * ImportPayload}.
   *
   * @param body the full JSON body
   */
  public ImportPayload toPayload(JsonNode body) {
    String schemaVersion = textOrNull(body, "schemaVersion");

    DeckMeta deck = null;
    JsonNode deckNode = body.path("deck");
    if (!deckNode.isMissingNode() && !deckNode.isNull()) {
      String title = textOrNull(deckNode, "title");
      String description = textOrNull(deckNode, "description");
      List<String> tags = toStringList(deckNode.path("tags"));
      deck = new DeckMeta(title, description, tags);
    }

    List<NoteImport> notes = new ArrayList<>();
    JsonNode notesNode = body.path("notes");
    if (notesNode.isArray()) {
      for (JsonNode noteNode : notesNode) {
        notes.add(toNoteImport(noteNode));
      }
    }

    return new ImportPayload(schemaVersion, deck, notes);
  }

  private NoteImport toNoteImport(JsonNode node) {
    String noteType = textOrNull(node, "noteType");
    String front = textOrNull(node, "front");
    String back = textOrNull(node, "back");
    String text = textOrNull(node, "text");
    String question = textOrNull(node, "question");
    String explanation = textOrNull(node, "explanation");
    String prompt = textOrNull(node, "prompt");
    String expectedAnswer = textOrNull(node, "expectedAnswer");
    String gradingGuidance = textOrNull(node, "gradingGuidance");
    List<String> tags = toStringList(node.path("tags"));

    List<OptionImport> options = null;
    JsonNode optsNode = node.path("options");
    if (optsNode.isArray()) {
      options = new ArrayList<>();
      for (JsonNode opt : optsNode) {
        options.add(new OptionImport(textOrNull(opt, "key"), textOrNull(opt, "text")));
      }
    }

    List<String> correctOptionKeys = toStringList(node.path("correctOptionKeys"));

    SourceRef source = null;
    JsonNode srcNode = node.path("source");
    if (!srcNode.isMissingNode() && !srcNode.isNull()) {
      source = new SourceRef(textOrNull(srcNode, "type"), textOrNull(srcNode, "reference"));
    }

    return new NoteImport(
        noteType,
        front,
        back,
        text,
        question,
        options,
        correctOptionKeys,
        explanation,
        prompt,
        expectedAnswer,
        gradingGuidance,
        tags,
        source);
  }

  // ---------------------------------------------------------------
  // Domain → web DTOs
  // ---------------------------------------------------------------

  public ImportValidationResponse toValidationResponse(ValidateImportUseCase.Result result) {
    var violations =
        result.errors().stream()
            .map(e -> new ImportValidationResponse.ViolationDto(e.field(), e.message(), e.code()))
            .toList();
    return new ImportValidationResponse(result.valid(), violations, result.warnings());
  }

  public ImportPreviewResponse toPreviewResponse(PreviewImportUseCase.Result result) {
    var summary = result.summary();
    var summaryDto =
        new ImportPreviewResponse.SummaryDto(
            summary.deckTitle(),
            summary.totalNotes(),
            summary.predictedCards(),
            summary.duplicateCandidates());
    return new ImportPreviewResponse(result.valid(), summaryDto, result.warnings());
  }

  public ImportResultResponse toResultResponse(ExecuteImportUseCase.ImportResult result) {
    return new ImportResultResponse(
        result.importId(),
        result.deckId(),
        result.importedNotes(),
        result.importedCards(),
        result.duplicateNotes(),
        result.rejectedNotes(),
        result.warnings());
  }

  /** Serializes a domain ImportPayload back to a simple map structure for export. */
  public JsonNode toJson(ImportPayload payload, ObjectMapper mapper) {
    try {
      return mapper.valueToTree(toExportMap(payload));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize export payload", e);
    }
  }

  private Map<String, Object> toExportMap(ImportPayload payload) {
    var map = new LinkedHashMap<String, Object>();
    map.put("schemaVersion", payload.schemaVersion());

    var deck = new LinkedHashMap<String, Object>();
    deck.put("title", payload.deck().title());
    if (payload.deck().description() != null) deck.put("description", payload.deck().description());
    if (payload.deck().tags() != null && !payload.deck().tags().isEmpty())
      deck.put("tags", payload.deck().tags());
    map.put("deck", deck);

    var notes = new ArrayList<>();
    for (NoteImport note : payload.notes()) {
      notes.add(toNoteMap(note));
    }
    map.put("notes", notes);
    return map;
  }

  private Map<String, Object> toNoteMap(NoteImport note) {
    var map = new LinkedHashMap<String, Object>();
    map.put("noteType", note.noteType());
    if (note.front() != null) map.put("front", note.front());
    if (note.back() != null) map.put("back", note.back());
    if (note.text() != null) map.put("text", note.text());
    if (note.question() != null) map.put("question", note.question());
    if (note.options() != null) {
      var opts = new ArrayList<>();
      for (var opt : note.options()) {
        var o = new LinkedHashMap<String, Object>();
        o.put("key", opt.key());
        o.put("text", opt.text());
        opts.add(o);
      }
      map.put("options", opts);
    }
    if (note.correctOptionKeys() != null) map.put("correctOptionKeys", note.correctOptionKeys());
    if (note.explanation() != null) map.put("explanation", note.explanation());
    if (note.prompt() != null) map.put("prompt", note.prompt());
    if (note.expectedAnswer() != null) map.put("expectedAnswer", note.expectedAnswer());
    if (note.gradingGuidance() != null) map.put("gradingGuidance", note.gradingGuidance());
    if (note.tags() != null && !note.tags().isEmpty()) map.put("tags", note.tags());
    return map;
  }

  // ---------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------

  private static String textOrNull(JsonNode node, String field) {
    JsonNode child = node.path(field);
    if (child.isMissingNode() || child.isNull() || child.getNodeType() != JsonNodeType.STRING)
      return null;
    String text = child.asString();
    return (text == null || text.isEmpty()) ? null : text;
  }

  private static List<String> toStringList(JsonNode node) {
    if (node.isMissingNode() || node.isNull() || !node.isArray()) return null;
    return StreamSupport.stream(node.spliterator(), false)
        .filter(n -> n.getNodeType() == JsonNodeType.STRING)
        .map(JsonNode::asString)
        .toList();
  }
}
