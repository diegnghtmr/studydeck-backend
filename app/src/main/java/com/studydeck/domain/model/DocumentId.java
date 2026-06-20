package com.studydeck.domain.model;

import java.util.UUID;

/** Strongly-typed identity for a {@link SourceDocument}. */
public record DocumentId(UUID value) {

  public DocumentId {
    if (value == null) throw new IllegalArgumentException("DocumentId must not be null");
  }

  public static DocumentId generate() {
    return new DocumentId(UUID.randomUUID());
  }

  public static DocumentId of(UUID value) {
    return new DocumentId(value);
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
