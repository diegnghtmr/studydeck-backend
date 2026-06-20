package com.studydeck.domain.model;

import java.util.UUID;

/** Strongly-typed identity for an {@link IngestJob}. */
public record IngestJobId(UUID value) {

  public IngestJobId {
    if (value == null) throw new IllegalArgumentException("IngestJobId must not be null");
  }

  public static IngestJobId generate() {
    return new IngestJobId(UUID.randomUUID());
  }

  public static IngestJobId of(UUID value) {
    return new IngestJobId(value);
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
