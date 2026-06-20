package com.studydeck.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studydeck.domain.exception.DomainValidationException;
import org.junit.jupiter.api.Test;

class SchedulerPresetTest {

  @Test
  void fsrsDefaultPresetIsValid() {
    SchedulerPreset preset = SchedulerPreset.fsrsDefault();

    assertThat(preset.algorithm()).isEqualTo(SchedulerAlgorithm.FSRS);
    assertThat(preset.desiredRetention()).isEqualTo(0.9);
    assertThat(preset.weights()).isNull();
  }

  @Test
  void sm2DefaultPresetIsValid() {
    SchedulerPreset preset = SchedulerPreset.sm2Default();

    assertThat(preset.algorithm()).isEqualTo(SchedulerAlgorithm.SM2);
    assertThat(preset.desiredRetention()).isEqualTo(0.9);
  }

  @Test
  void retentionBoundaryValuesAreAccepted() {
    SchedulerPreset low = SchedulerPreset.defaults(SchedulerAlgorithm.FSRS, 0.70);
    assertThat(low.desiredRetention()).isEqualTo(0.70);

    SchedulerPreset high = SchedulerPreset.defaults(SchedulerAlgorithm.FSRS, 0.99);
    assertThat(high.desiredRetention()).isEqualTo(0.99);
  }

  @Test
  void retentionBelowMinIsRejected() {
    assertThatThrownBy(() -> SchedulerPreset.defaults(SchedulerAlgorithm.FSRS, 0.69))
        .isInstanceOf(DomainValidationException.class)
        .hasMessageContaining("desiredRetention");
  }

  @Test
  void retentionAboveMaxIsRejected() {
    assertThatThrownBy(() -> SchedulerPreset.defaults(SchedulerAlgorithm.FSRS, 1.0))
        .isInstanceOf(DomainValidationException.class)
        .hasMessageContaining("desiredRetention");
  }

  @Test
  void nullAlgorithmIsRejected() {
    assertThatThrownBy(() -> SchedulerPreset.defaults(null, 0.9))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("algorithm");
  }

  @Test
  void wrongWeightLengthForFsrsIsRejected() {
    double[] badWeights = new double[10]; // should be 19
    assertThatThrownBy(() -> new SchedulerPreset(SchedulerAlgorithm.FSRS, 0.9, badWeights))
        .isInstanceOf(DomainValidationException.class)
        .hasMessageContaining("weights");
  }

  @Test
  void correctWeightLengthForFsrsIsAccepted() {
    double[] goodWeights = new double[SchedulerPreset.FSRS_PARAM_COUNT];
    SchedulerPreset preset = new SchedulerPreset(SchedulerAlgorithm.FSRS, 0.9, goodWeights);
    assertThat(preset.weights()).isNotNull();
    assertThat(preset.weights()).hasSize(SchedulerPreset.FSRS_PARAM_COUNT);
  }
}
