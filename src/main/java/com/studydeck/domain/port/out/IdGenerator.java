package com.studydeck.domain.port.out;

import java.util.UUID;

/**
 * Output port — supplies IDs for new entities.
 *
 * <p>Keeps use cases deterministic/testable: tests inject a predictable sequence; production uses
 * {@code UUID::randomUUID}.
 */
public interface IdGenerator {

  /** Returns a new unique UUID. */
  UUID generate();
}
