package com.studydeck.infrastructure.adapter.out.persistence;

import com.studydeck.domain.port.out.UserAiProviderRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link UserAiProviderRepository} bean.
 *
 * <p>Lives in the same package as the adapters to access package-private classes. Follows the same
 * pattern as {@link PersistenceConfiguration}.
 */
@Configuration
class UserAiProviderPersistenceConfiguration {

  @Bean
  UserAiProviderMapper userAiProviderMapper() {
    return new UserAiProviderMapper();
  }

  @Bean
  UserAiProviderRepository userAiProviderRepository(
      UserAiProviderJpaRepository jpaRepo, UserAiProviderMapper mapper) {
    return new UserAiProviderPersistenceAdapter(jpaRepo, mapper);
  }
}
