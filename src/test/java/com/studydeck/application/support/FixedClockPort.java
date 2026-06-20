package com.studydeck.application.support;

import com.studydeck.domain.port.out.ClockPort;
import java.time.Instant;

/** Test double for {@link ClockPort} that always returns a fixed instant. */
public final class FixedClockPort implements ClockPort {

  private Instant fixedInstant;

  public FixedClockPort(Instant fixedInstant) {
    this.fixedInstant = fixedInstant;
  }

  public static FixedClockPort at(Instant instant) {
    return new FixedClockPort(instant);
  }

  public static FixedClockPort epoch() {
    return new FixedClockPort(Instant.EPOCH);
  }

  @Override
  public Instant now() {
    return fixedInstant;
  }

  public void setFixedInstant(Instant newInstant) {
    this.fixedInstant = newInstant;
  }
}
