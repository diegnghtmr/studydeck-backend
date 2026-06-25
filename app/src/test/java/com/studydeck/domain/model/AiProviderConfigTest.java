package com.studydeck.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AiProviderConfigTest {

  @Test
  @DisplayName("valid construction succeeds")
  void validConstruction() {
    var config = new AiProviderConfig("https://api.openai.com/v1", "sk-test", "gpt-4o-mini");
    assertThat(config.baseUrl()).isEqualTo("https://api.openai.com/v1");
    assertThat(config.apiKey()).isEqualTo("sk-test");
    assertThat(config.model()).isEqualTo("gpt-4o-mini");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "\t"})
  @DisplayName("blank baseUrl throws IllegalArgumentException")
  void blankBaseUrlThrows(String baseUrl) {
    assertThatThrownBy(() -> new AiProviderConfig(baseUrl, "sk-test", "gpt-4o-mini"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("baseUrl");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "\t"})
  @DisplayName("blank apiKey throws IllegalArgumentException")
  void blankApiKeyThrows(String apiKey) {
    assertThatThrownBy(
            () -> new AiProviderConfig("https://api.openai.com/v1", apiKey, "gpt-4o-mini"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("apiKey");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "\t"})
  @DisplayName("blank model throws IllegalArgumentException")
  void blankModelThrows(String model) {
    assertThatThrownBy(() -> new AiProviderConfig("https://api.openai.com/v1", "sk-test", model))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("model");
  }
}
