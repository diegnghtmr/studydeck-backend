package com.studydeck.domain.model;

/**
 * Value object representing a per-request AI provider configuration (BYOK).
 *
 * <p>All three fields are required when this object is present. Use {@code null} at the call site
 * to indicate "use the server-global provider".
 *
 * <p>Pure Java — no Spring or Jakarta imports (ArchUnit enforces domain layer purity).
 */
public record AiProviderConfig(String baseUrl, String apiKey, String model) {
  public AiProviderConfig {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("AiProviderConfig.baseUrl must not be null or blank");
    }
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("AiProviderConfig.apiKey must not be null or blank");
    }
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("AiProviderConfig.model must not be null or blank");
    }
  }
}
