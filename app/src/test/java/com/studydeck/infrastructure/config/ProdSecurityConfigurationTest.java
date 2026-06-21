package com.studydeck.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ProdSecurityConfiguration}.
 *
 * <p>Security contract: under the prod profile the application MUST refuse to start unless a real
 * OIDC issuer is configured. This closes the silent fallback where an empty {@code issuer-uri}
 * would otherwise let the dev HS256 decoder (with a known, source-committed secret) authenticate
 * prod traffic.
 */
class ProdSecurityConfigurationTest {

  @Test
  @DisplayName("blank issuer-uri fails fast")
  void blankIssuerFailsFast() {
    assertThatThrownBy(() -> new ProdSecurityConfiguration("  "))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ISSUER_URI");
  }

  @Test
  @DisplayName("null issuer-uri fails fast")
  void nullIssuerFailsFast() {
    assertThatThrownBy(() -> new ProdSecurityConfiguration(null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("a real issuer-uri starts cleanly")
  void realIssuerStarts() {
    assertThatCode(() -> new ProdSecurityConfiguration("https://idp.example.com/realms/studydeck"))
        .doesNotThrowAnyException();
    assertThat(new ProdSecurityConfiguration("https://idp.example.com").issuerUri())
        .isEqualTo("https://idp.example.com");
  }
}
