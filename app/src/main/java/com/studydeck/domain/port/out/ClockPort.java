package com.studydeck.domain.port.out;

import java.time.Instant;

/**
 * Output port — supplies the current wall-clock time.
 *
 * <p>Keeps use cases deterministic/testable: tests inject a fixed instant; production delegates to
 * {@code Instant::now}.
 */
public interface ClockPort {

  /** Returns the current instant. */
  Instant now();
}
