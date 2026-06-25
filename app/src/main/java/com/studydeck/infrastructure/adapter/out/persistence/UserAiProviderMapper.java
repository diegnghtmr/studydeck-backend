package com.studydeck.infrastructure.adapter.out.persistence;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.UserAiProvider;
import com.studydeck.domain.model.UserAiProviderId;

/**
 * Mapper between {@link UserAiProvider} domain aggregates and {@link UserAiProviderJpaEntity}.
 *
 * <p>Ciphertext and key hint are moved verbatim — no crypto operations happen here.
 * Package-private, accessed only from {@link UserAiProviderPersistenceAdapter} and {@link
 * UserAiProviderPersistenceConfiguration}.
 */
class UserAiProviderMapper {

  UserAiProvider toDomain(UserAiProviderJpaEntity e) {
    return UserAiProvider.create(
        new UserAiProviderId(e.getId()),
        new OwnerId(e.getOwnerId()),
        e.getLabel(),
        e.getBaseUrl(),
        e.getModel(),
        e.getApiKeyCiphertext(),
        e.getKeyHint(),
        e.isActive(),
        e.getCreatedAt(),
        e.getUpdatedAt());
  }

  UserAiProviderJpaEntity toJpa(UserAiProvider provider) {
    UserAiProviderJpaEntity e = new UserAiProviderJpaEntity();
    e.setId(provider.getId().value());
    e.setOwnerId(provider.getOwnerId().value());
    e.setLabel(provider.getLabel());
    e.setBaseUrl(provider.getBaseUrl());
    e.setModel(provider.getModel());
    e.setApiKeyCiphertext(provider.getApiKeyCiphertext());
    e.setKeyHint(provider.getKeyHint());
    e.setActive(provider.isActive());
    e.setCreatedAt(provider.getCreatedAt());
    e.setUpdatedAt(provider.getUpdatedAt());
    return e;
  }
}
