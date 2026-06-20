package com.studydeck.cli.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TokenResolverTest {

  @TempDir Path tempDir;

  private TokenResolver resolverWithNoEnv() {
    return new TokenResolver(name -> null, tempDir.resolve("config"));
  }

  private TokenResolver resolverWithEnv(String envToken) {
    return new TokenResolver(
        name -> "STUDYDECK_TOKEN".equals(name) ? envToken : null, tempDir.resolve("config"));
  }

  private TokenResolver resolverWithConfigFile(String fileContent) throws IOException {
    Path config = tempDir.resolve("config");
    Files.writeString(config, fileContent);
    return new TokenResolver(name -> null, config);
  }

  // ── Priority 1: --token flag ────────────────────────────────────────────────

  @Test
  void shouldReturnFlagTokenWhenProvided() {
    TokenResolver resolver = resolverWithNoEnv();
    assertThat(resolver.resolve("flag-token-xyz")).isEqualTo("flag-token-xyz");
  }

  @Test
  void shouldTrimWhitespaceFromFlagToken() {
    TokenResolver resolver = resolverWithNoEnv();
    assertThat(resolver.resolve("  my-token  ")).isEqualTo("my-token");
  }

  @Test
  void shouldPreferFlagTokenOverEnvVar() {
    TokenResolver resolver = resolverWithEnv("env-token");
    assertThat(resolver.resolve("flag-token")).isEqualTo("flag-token");
  }

  // ── Priority 2: env var ─────────────────────────────────────────────────────

  @Test
  void shouldReturnEnvTokenWhenFlagIsEmpty() {
    TokenResolver resolver = resolverWithEnv("env-token-abc");
    assertThat(resolver.resolve("")).isEqualTo("env-token-abc");
  }

  @Test
  void shouldReturnEnvTokenWhenFlagIsNull() {
    TokenResolver resolver = resolverWithEnv("env-token-abc");
    assertThat(resolver.resolve(null)).isEqualTo("env-token-abc");
  }

  @Test
  void shouldReturnEnvTokenWhenFlagIsBlank() {
    TokenResolver resolver = resolverWithEnv("env-token-abc");
    assertThat(resolver.resolve("   ")).isEqualTo("env-token-abc");
  }

  // ── Priority 3: config file ─────────────────────────────────────────────────

  @Test
  void shouldReturnConfigFileTokenWhenNoFlagOrEnv() throws IOException {
    TokenResolver resolver = resolverWithConfigFile("token=config-file-token\n");
    assertThat(resolver.resolve(null)).isEqualTo("config-file-token");
  }

  @Test
  void shouldReturnConfigFileTokenWhenEnvIsEmpty() throws IOException {
    Path config = tempDir.resolve("config");
    Files.writeString(config, "token=config-token\n");
    TokenResolver resolver = new TokenResolver(name -> null, config);
    assertThat(resolver.resolve(null)).isEqualTo("config-token");
  }

  // ── Not found ───────────────────────────────────────────────────────────────

  @Test
  void shouldThrowTokenNotFoundExceptionWhenNoSourceHasToken() {
    TokenResolver resolver = resolverWithNoEnv();
    assertThatThrownBy(() -> resolver.resolve(null))
        .isInstanceOf(TokenNotFoundException.class)
        .hasMessageContaining("No auth token found");
  }

  @Test
  void shouldThrowTokenNotFoundWhenConfigFileIsMissing() {
    TokenResolver resolver = resolverWithNoEnv();
    assertThatThrownBy(() -> resolver.resolve(null)).isInstanceOf(TokenNotFoundException.class);
  }

  // ── Store / clear ───────────────────────────────────────────────────────────

  @Test
  void shouldStoreTokenToConfigFile() throws IOException {
    Path config = tempDir.resolve("config");
    TokenResolver resolver = new TokenResolver(name -> null, config);

    resolver.storeToken("stored-token-123");

    assertThat(Files.exists(config)).isTrue();
    assertThat(resolver.resolve(null)).isEqualTo("stored-token-123");
  }

  @Test
  void shouldClearTokenFromConfigFile() throws IOException {
    Path config = tempDir.resolve("config");
    Files.writeString(config, "token=old-token\n");
    TokenResolver resolver = new TokenResolver(name -> null, config);

    resolver.clearToken();

    assertThatThrownBy(() -> resolver.resolve(null)).isInstanceOf(TokenNotFoundException.class);
  }

  @Test
  void shouldHandleClearWhenConfigFileDoesNotExist() {
    Path config = tempDir.resolve("nonexistent-config");
    TokenResolver resolver = new TokenResolver(name -> null, config);
    // Should not throw
    org.junit.jupiter.api.Assertions.assertDoesNotThrow(resolver::clearToken);
  }

  @Test
  void shouldCreateParentDirectoriesWhenStoringToken() throws IOException {
    Path nestedConfig = tempDir.resolve("nested").resolve("dir").resolve("config");
    TokenResolver resolver = new TokenResolver(name -> null, nestedConfig);

    resolver.storeToken("new-token");

    assertThat(Files.exists(nestedConfig)).isTrue();
    assertThat(resolver.resolve(null)).isEqualTo("new-token");
  }
}
