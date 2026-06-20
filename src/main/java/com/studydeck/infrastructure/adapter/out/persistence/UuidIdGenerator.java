package com.studydeck.infrastructure.adapter.out.persistence;

import com.studydeck.domain.port.out.IdGenerator;
import java.util.UUID;

/** Production implementation of {@link IdGenerator} — delegates to {@link UUID#randomUUID()}. */
class UuidIdGenerator implements IdGenerator {

  @Override
  public UUID generate() {
    return UUID.randomUUID();
  }
}
