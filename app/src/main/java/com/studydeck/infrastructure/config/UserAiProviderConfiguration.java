package com.studydeck.infrastructure.config;

import com.studydeck.application.service.UserAiProviderService;
import com.studydeck.domain.port.in.DeleteUserAiProviderUseCase;
import com.studydeck.domain.port.in.GetActiveUserAiProviderQuery;
import com.studydeck.domain.port.in.ListUserAiProvidersQuery;
import com.studydeck.domain.port.in.SaveUserAiProviderUseCase;
import com.studydeck.domain.port.out.ClockPort;
import com.studydeck.domain.port.out.CryptoPort;
import com.studydeck.domain.port.out.IdGenerator;
import com.studydeck.domain.port.out.UserAiProviderRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires AI provider application service as Spring beans.
 *
 * <p>Pattern: Explicit {@code @Bean} (Purist). Domain and application layers stay Spring-free. All
 * wiring happens here in the infrastructure layer.
 *
 * <p>Convention: concrete {@link UserAiProviderService} is NEVER exposed as a Spring bean. Only the
 * individual input port interfaces are registered. This prevents Spring from seeing ambiguous
 * candidates when a controller injects an input port interface.
 *
 * <p>Follows the same pattern as {@link BeanConfiguration} and {@link AiConfiguration}.
 */
@Configuration
public class UserAiProviderConfiguration {

  private UserAiProviderService userAiProviderService(
      UserAiProviderRepository repository,
      CryptoPort cryptoPort,
      IdGenerator idGenerator,
      ClockPort clockPort) {
    return new UserAiProviderService(repository, cryptoPort, idGenerator, clockPort);
  }

  @Bean
  SaveUserAiProviderUseCase saveUserAiProviderUseCase(
      UserAiProviderRepository repository,
      CryptoPort cryptoPort,
      IdGenerator idGenerator,
      ClockPort clockPort) {
    return userAiProviderService(repository, cryptoPort, idGenerator, clockPort);
  }

  @Bean
  ListUserAiProvidersQuery listUserAiProvidersQuery(
      UserAiProviderRepository repository,
      CryptoPort cryptoPort,
      IdGenerator idGenerator,
      ClockPort clockPort) {
    return userAiProviderService(repository, cryptoPort, idGenerator, clockPort);
  }

  @Bean
  DeleteUserAiProviderUseCase deleteUserAiProviderUseCase(
      UserAiProviderRepository repository,
      CryptoPort cryptoPort,
      IdGenerator idGenerator,
      ClockPort clockPort) {
    return userAiProviderService(repository, cryptoPort, idGenerator, clockPort);
  }

  @Bean
  GetActiveUserAiProviderQuery getActiveUserAiProviderQuery(
      UserAiProviderRepository repository,
      CryptoPort cryptoPort,
      IdGenerator idGenerator,
      ClockPort clockPort) {
    return userAiProviderService(repository, cryptoPort, idGenerator, clockPort);
  }
}
