package com.studydeck.domain.model;

import com.studydeck.domain.exception.DomainValidationException;
import java.util.Objects;

/**
 * Value object combining algorithm choice, optional custom parameters, and desired retention.
 *
 * <p>A preset is immutable once created. Custom weights are represented as a raw double array
 * aligned to the FSRS or SM-2 parameter vector. A {@code null} weights array means "use canonical
 * defaults for the chosen algorithm".
 *
 * <p>Invariants:
 *
 * <ul>
 *   <li>{@code algorithm} — non-null
 *   <li>{@code desiredRetention} — 0.70–0.99 inclusive
 *   <li>{@code weights} — may be null (use defaults); if non-null, length must match algorithm
 * </ul>
 *
 * <p>Pure Java — no Spring, no JPA annotations.
 */
public record SchedulerPreset(
    SchedulerAlgorithm algorithm, double desiredRetention, double[] weights) {

  /** Expected parameter count for FSRS (v5 / v5.5 family). */
  public static final int FSRS_PARAM_COUNT = 19;

  /** Expected parameter count for SM-2 (no learnable parameters, conventional). */
  public static final int SM2_PARAM_COUNT = 0;

  private static final double MIN_RETENTION = 0.70;
  private static final double MAX_RETENTION = 0.99;

  public SchedulerPreset {
    Objects.requireNonNull(algorithm, "algorithm must not be null");
    if (desiredRetention < MIN_RETENTION || desiredRetention > MAX_RETENTION) {
      throw new DomainValidationException(
          "desiredRetention", "must be in [0.70, 0.99], got " + desiredRetention);
    }
    if (weights != null) {
      int expected = expectedParamCount(algorithm);
      if (expected > 0 && weights.length != expected) {
        throw new DomainValidationException(
            "weights",
            "expected %d params for %s, got %d".formatted(expected, algorithm, weights.length));
      }
    }
  }

  /**
   * Creates a preset using algorithm defaults (no custom weights).
   *
   * @param algorithm the algorithm to use
   * @param desiredRetention 0.70–0.99
   */
  public static SchedulerPreset defaults(SchedulerAlgorithm algorithm, double desiredRetention) {
    return new SchedulerPreset(algorithm, desiredRetention, null);
  }

  /** Returns the FSRS default preset with 0.9 desired retention. */
  public static SchedulerPreset fsrsDefault() {
    return defaults(SchedulerAlgorithm.FSRS, 0.9);
  }

  /** Returns the SM-2 default preset with 0.9 desired retention. */
  public static SchedulerPreset sm2Default() {
    return defaults(SchedulerAlgorithm.SM2, 0.9);
  }

  private static int expectedParamCount(SchedulerAlgorithm algorithm) {
    return switch (algorithm) {
      case FSRS -> FSRS_PARAM_COUNT;
      case SM2 -> SM2_PARAM_COUNT;
    };
  }
}
