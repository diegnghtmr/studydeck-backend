package com.studydeck.domain.port.in;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.UserAiProviderId;
import java.time.Instant;
import java.util.List;

/**
 * Input port for listing a user's AI provider configurations.
 *
 * <p>Returns masked views only — no plaintext or ciphertext is exposed. Pure Java — no Spring, no
 * Jakarta imports.
 */
public interface ListUserAiProvidersQuery {

  List<Masked> list(OwnerId ownerId);

  /**
   * Masked view of a provider returned to callers. Contains the key hint but NOT the plaintext key
   * or ciphertext.
   */
  record Masked(
      UserAiProviderId id,
      String label,
      String baseUrl,
      String model,
      String keyHint,
      boolean active,
      Instant createdAt,
      Instant updatedAt) {}
}
