package com.studydeck.cli.auth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/**
 * Resolves the bearer token in priority order:
 *
 * <ol>
 *   <li>Explicit --token flag (passed as flagToken parameter)
 *   <li>STUDYDECK_TOKEN environment variable
 *   <li>~/.config/studydeck/config file (key: token)
 * </ol>
 *
 * <p>Auth note: the intended production flow is OAuth2 Device Authorization Grant. The token
 * obtained via the device grant should be stored in ~/.config/studydeck/config and will be picked
 * up here automatically.
 */
public class TokenResolver {

  private final EnvironmentProvider environment;
  private final Path configFile;

  public TokenResolver() {
    this(System::getenv, defaultConfigPath());
  }

  TokenResolver(EnvironmentProvider environment, Path configFile) {
    this.environment = environment;
    this.configFile = configFile;
  }

  private static Path defaultConfigPath() {
    return Path.of(System.getProperty("user.home"), ".config", "studydeck", "config");
  }

  /**
   * Resolves the token using the priority chain.
   *
   * @param flagToken token passed via --token flag (may be null or empty)
   * @return resolved token
   * @throws TokenNotFoundException if no token is found in any source
   */
  public String resolve(String flagToken) {
    // 1. --token flag
    if (flagToken != null && !flagToken.isBlank()) {
      return flagToken.trim();
    }

    // 2. Environment variable
    String envToken = environment.getenv("STUDYDECK_TOKEN");
    if (envToken != null && !envToken.isBlank()) {
      return envToken.trim();
    }

    // 3. Config file
    Optional<String> fileToken = readTokenFromConfig();
    if (fileToken.isPresent()) {
      return fileToken.get();
    }

    throw new TokenNotFoundException(
        "No auth token found. Set STUDYDECK_TOKEN env var, use --token flag, "
            + "or run 'studydeck auth login' to authenticate.");
  }

  private Optional<String> readTokenFromConfig() {
    if (!Files.exists(configFile)) {
      return Optional.empty();
    }
    Properties props = new Properties();
    try (var reader = Files.newBufferedReader(configFile)) {
      props.load(reader);
      String token = props.getProperty("token");
      if (token != null && !token.isBlank()) {
        return Optional.of(token.trim());
      }
    } catch (IOException e) {
      // Config file unreadable — treat as not found
    }
    return Optional.empty();
  }

  /** Stores a token to the config file. Creates parent directories as needed. */
  public void storeToken(String token) throws IOException {
    Files.createDirectories(configFile.getParent());
    Properties props = new Properties();
    if (Files.exists(configFile)) {
      try (var reader = Files.newBufferedReader(configFile)) {
        props.load(reader);
      }
    }
    props.setProperty("token", token);
    try (var writer = Files.newBufferedWriter(configFile)) {
      props.store(writer, "StudyDeck CLI config — do not share this file");
    }
  }

  /** Removes the stored token from the config file. */
  public void clearToken() throws IOException {
    if (!Files.exists(configFile)) {
      return;
    }
    Properties props = new Properties();
    try (var reader = Files.newBufferedReader(configFile)) {
      props.load(reader);
    }
    props.remove("token");
    try (var writer = Files.newBufferedWriter(configFile)) {
      props.store(writer, "StudyDeck CLI config");
    }
  }

  @FunctionalInterface
  public interface EnvironmentProvider {
    String getenv(String name);
  }
}
