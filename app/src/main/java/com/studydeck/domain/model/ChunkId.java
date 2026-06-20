package com.studydeck.domain.model;

import java.util.UUID;

/** Strongly-typed identity for a {@link DocumentChunk}. */
public record ChunkId(UUID value) {

  public ChunkId {
    if (value == null) throw new IllegalArgumentException("ChunkId must not be null");
  }

  public static ChunkId generate() {
    return new ChunkId(UUID.randomUUID());
  }

  public static ChunkId of(UUID value) {
    return new ChunkId(value);
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
