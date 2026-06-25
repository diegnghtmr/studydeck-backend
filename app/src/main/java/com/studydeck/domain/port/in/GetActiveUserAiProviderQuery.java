package com.studydeck.domain.port.in;

import com.studydeck.domain.model.AiProviderConfig;
import com.studydeck.domain.model.OwnerId;
import java.util.Optional;

/**
 * Input port for resolving the active AI provider for a given user.
 *
 * <p>This is the only read path that produces plaintext (decrypts the stored key). The result is
 * server-internal only — it is NEVER serialized to a response body.
 *
 * <p>Pure Java — no Spring, no Jakarta imports.
 */
public interface GetActiveUserAiProviderQuery {

  /**
   * Returns the active provider's configuration with the decrypted API key, or {@link
   * Optional#empty()} if the user has no active provider.
   */
  Optional<AiProviderConfig> execute(OwnerId ownerId);
}
