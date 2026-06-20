package com.studydeck.domain.port.in;

import com.studydeck.domain.model.OwnerId;
import java.util.List;

/**
 * Input port — validates a flashcard import payload (schema + domain) without persisting anything.
 *
 * <p>Returns per-field errors and warnings. A valid result has {@code valid = true} and empty
 * errors.
 */
public interface ValidateImportUseCase {

  Result execute(Command command);

  record Command(OwnerId ownerId, ImportPayload payload) {}

  record Result(boolean valid, List<Violation> errors, List<String> warnings) {

    public static Result valid(List<String> warnings) {
      return new Result(true, List.of(), warnings);
    }

    public static Result invalid(List<Violation> errors, List<String> warnings) {
      return new Result(false, errors, warnings);
    }
  }

  record Violation(String field, String message, String code) {

    public static Violation of(String field, String message) {
      return new Violation(field, message, null);
    }
  }

  /** Parsed, structured import payload (schema-validated JSON already deserialized). */
  record ImportPayload(String schemaVersion, DeckMeta deck, List<NoteImport> notes) {

    public record DeckMeta(String title, String description, List<String> tags) {}

    public record NoteImport(
        String noteType,
        String front,
        String back,
        String text,
        String question,
        List<OptionImport> options,
        List<String> correctOptionKeys,
        String explanation,
        String prompt,
        String expectedAnswer,
        String gradingGuidance,
        List<String> tags,
        SourceRef source) {}

    public record OptionImport(String key, String text) {}

    public record SourceRef(String type, String reference) {}
  }
}
