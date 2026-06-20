package com.studydeck.domain.service;

import com.studydeck.domain.model.NoteContent;
import com.studydeck.domain.port.in.ValidateImportUseCase.ImportPayload.NoteImport;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Domain service — computes a stable, normalized content hash for duplicate detection.
 *
 * <p>Hash algorithm: SHA-256 of a normalized canonical string representation of the note content,
 * keyed by note type. The normalization trims whitespace and lowercases all text so that minor
 * whitespace differences do not produce different hashes.
 *
 * <p>Pure Java — no Spring, no I/O.
 */
public final class NoteContentHasher {

  private NoteContentHasher() {}

  /**
   * Computes the content hash from a {@link NoteContent} domain object (for post-persist storage).
   */
  public static String hash(NoteContent content) {
    String canonical =
        switch (content) {
          case NoteContent.Basic b -> "basic:" + normalize(b.front()) + "|" + normalize(b.back());
          case NoteContent.Reversed r ->
              "reversed:" + normalize(r.front()) + "|" + normalize(r.back());
          case NoteContent.Cloze c -> "cloze:" + normalize(c.text());
          case NoteContent.MultipleChoice mc -> hashMultipleChoice(mc);
          case NoteContent.FreeText ft ->
              "free-text:" + normalize(ft.prompt()) + "|" + normalize(ft.expectedAnswer());
        };
    return sha256Hex(canonical);
  }

  /**
   * Computes the content hash from a raw {@link NoteImport} DTO (for preview/dedup before domain
   * object creation).
   */
  public static String hash(NoteImport note) {
    String canonical =
        switch (note.noteType()) {
          case "basic" -> "basic:" + normalize(note.front()) + "|" + normalize(note.back());
          case "reversed" -> "reversed:" + normalize(note.front()) + "|" + normalize(note.back());
          case "cloze" -> "cloze:" + normalize(note.text());
          case "multiple-choice" -> hashMultipleChoiceImport(note);
          case "free-text" ->
              "free-text:" + normalize(note.prompt()) + "|" + normalize(note.expectedAnswer());
          default -> note.noteType() + ":" + note.toString();
        };
    return sha256Hex(canonical);
  }

  // ---------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------

  private static String hashMultipleChoice(NoteContent.MultipleChoice mc) {
    StringBuilder sb = new StringBuilder("multiple-choice:");
    sb.append(normalize(mc.question()));
    for (NoteContent.MultipleChoice.Option opt : mc.options()) {
      sb.append("|").append(opt.key()).append(":").append(normalize(opt.text()));
    }
    sb.append("|correct:");
    List<String> sortedKeys = mc.correctOptionKeys().stream().sorted().toList();
    sb.append(String.join(",", sortedKeys));
    return sha256Hex(sb.toString());
  }

  private static String hashMultipleChoiceImport(NoteImport note) {
    StringBuilder sb = new StringBuilder("multiple-choice:");
    sb.append(normalize(note.question()));
    if (note.options() != null) {
      for (var opt : note.options()) {
        sb.append("|").append(opt.key()).append(":").append(normalize(opt.text()));
      }
    }
    sb.append("|correct:");
    if (note.correctOptionKeys() != null) {
      List<String> sortedKeys = note.correctOptionKeys().stream().sorted().toList();
      sb.append(String.join(",", sortedKeys));
    }
    return sha256Hex(sb.toString());
  }

  private static String normalize(String value) {
    if (value == null) return "";
    return value.strip().toLowerCase();
  }

  private static String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
