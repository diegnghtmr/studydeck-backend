package com.studydeck.domain.model;

import java.util.UUID;

/** Strongly-typed identity for an {@link EmbeddingRecord}. */
public record EmbeddingRecordId(UUID value) {

  public EmbeddingRecordId {
    if (value == null) throw new IllegalArgumentException("EmbeddingRecordId must not be null");
  }

  public static EmbeddingRecordId generate() {
    return new EmbeddingRecordId(UUID.randomUUID());
  }

  public static EmbeddingRecordId of(UUID value) {
    return new EmbeddingRecordId(value);
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
