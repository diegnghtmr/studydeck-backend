package com.studydeck.domain.port.out;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.UserAiProvider;
import com.studydeck.domain.model.UserAiProviderId;
import java.util.List;
import java.util.Optional;

/**
 * Output port for persisting and retrieving {@link UserAiProvider} aggregates.
 *
 * <p>All finders are owner-scoped to enforce IDOR isolation at the port level. Pure Java interface
 * — no Spring, no Jakarta imports.
 */
public interface UserAiProviderRepository {

  void save(UserAiProvider provider);

  /**
   * Finds a provider by id only if it belongs to the given owner. Returns empty for cross-owner ids
   * (IDOR protection — 404, not 403).
   */
  Optional<UserAiProvider> findByIdAndOwner(UserAiProviderId id, OwnerId owner);

  List<UserAiProvider> findAllByOwner(OwnerId owner);

  Optional<UserAiProvider> findActiveByOwner(OwnerId owner);

  void deleteByIdAndOwner(UserAiProviderId id, OwnerId owner);

  /** Sets {@code active = false} for every provider owned by the given user. */
  void deactivateAllForOwner(OwnerId owner);
}
