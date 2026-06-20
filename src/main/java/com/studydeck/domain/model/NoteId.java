package com.studydeck.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Value object wrapping the UUID primary key for a Note aggregate. */
public record NoteId(UUID value) {

  public NoteId {
    Objects.requireNonNull(value, "NoteId.value must not be null");
  }

  public static NoteId generate() {
    return new NoteId(UUID.randomUUID());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
