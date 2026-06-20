package com.studydeck.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * API-layer representation of a NoteType as kebab-case string.
 *
 * <p>Serializes to the OpenAPI-canonical values: basic, reversed, cloze, multiple-choice,
 * free-text. Deserialization uses the same values.
 *
 * <p>This is separate from the domain {@link com.studydeck.domain.model.NoteType} enum to avoid
 * coupling the domain to the wire format.
 */
public enum NoteTypeValue {
  BASIC("basic"),
  REVERSED("reversed"),
  CLOZE("cloze"),
  MULTIPLE_CHOICE("multiple-choice"),
  FREE_TEXT("free-text");

  private final String value;

  NoteTypeValue(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  /** Parses a kebab-case string value to the enum constant. */
  public static NoteTypeValue fromValue(String value) {
    for (NoteTypeValue v : values()) {
      if (v.value.equals(value)) {
        return v;
      }
    }
    throw new IllegalArgumentException("Unknown NoteType value: " + value);
  }
}
