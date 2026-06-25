package com.studydeck.domain.port.in;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.UserAiProviderId;

/**
 * Input port for deleting a user's AI provider configuration.
 *
 * <p>Pure Java — no Spring, no Jakarta imports.
 */
public interface DeleteUserAiProviderUseCase {

  void execute(Command command);

  /**
   * @param ownerId the authenticated user's ID (scope enforcement)
   * @param providerId the ID of the provider to delete
   */
  record Command(OwnerId ownerId, UserAiProviderId providerId) {}
}
