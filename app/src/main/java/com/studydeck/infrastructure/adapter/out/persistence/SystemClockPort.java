package com.studydeck.infrastructure.adapter.out.persistence;

import com.studydeck.domain.port.out.ClockPort;
import java.time.Instant;

/** Production implementation of {@link ClockPort} — delegates to {@link Instant#now()}. */
class SystemClockPort implements ClockPort {

  @Override
  public Instant now() {
    return Instant.now();
  }
}
