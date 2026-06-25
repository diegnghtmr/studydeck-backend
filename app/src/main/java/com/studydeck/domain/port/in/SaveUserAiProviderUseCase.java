package com.studydeck.domain.port.in;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.UserAiProviderId;
import java.time.Instant;

/**
 * Input port for creating and updating a user's AI provider configuration.
 *
 * <p>Handles both create ({@code idOrNull == null}) and update ({@code idOrNull != null}). Pure
 * Java — no Spring, no Jakarta imports.
 */
public interface SaveUserAiProviderUseCase {

  Result save(Command command);

  /**
   * Command to create or update a provider.
   *
   * @param ownerId the authenticated user's ID
   * @param idOrNull {@code null} = create; existing id = update
   * @param label human-readable label
   * @param baseUrl AI provider base URL
   * @param model model identifier
   * @param plaintextApiKey plaintext key to encrypt; {@code null} = preserve existing key on update
   * @param setActive whether to make this provider the active one
   */
  record Command(
      OwnerId ownerId,
      UserAiProviderId idOrNull,
      String label,
      String baseUrl,
      String model,
      String plaintextApiKey,
      boolean setActive) {}

  /**
   * Result returned after a successful save.
   *
   * @param id the provider's ID
   * @param keyHint the masked key hint (never the plaintext key)
   * @param active whether the provider is now active
   * @param createdAt when the provider was originally created
   * @param updatedAt when the provider was last updated
   */
  record Result(
      UserAiProviderId id,
      String label,
      String baseUrl,
      String model,
      String keyHint,
      boolean active,
      Instant createdAt,
      Instant updatedAt) {}
}
