package com.studydeck.domain.port.in;

import com.studydeck.domain.model.NoteType;
import java.util.List;

/** Input port — returns the supported note types and their field schemas. */
public interface ListNoteTypesQuery {

  /**
   * Returns all supported note type descriptors.
   *
   * @return ordered list of note type descriptors (never empty)
   */
  List<NoteTypeDescriptor> execute();

  /**
   * Descriptor for a single supported note type.
   *
   * @param noteType the enum constant
   * @param label human-readable label (e.g. "Basic flashcard")
   * @param description brief description of the note type
   * @param fields ordered list of field descriptors (for UI and MCP schema)
   */
  record NoteTypeDescriptor(
      NoteType noteType, String label, String description, List<FieldDescriptor> fields) {}

  /**
   * Descriptor for a single field within a note type.
   *
   * @param name field name as used in the API (e.g. "front", "text")
   * @param required whether this field is required
   * @param maxLength max character length; 0 means no limit
   * @param description brief description of the field's purpose
   */
  record FieldDescriptor(String name, boolean required, int maxLength, String description) {}
}
