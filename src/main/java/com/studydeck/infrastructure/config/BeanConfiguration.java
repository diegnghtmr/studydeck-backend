package com.studydeck.infrastructure.config;

import org.springframework.context.annotation.Configuration;

/**
 * Wires application services (use cases) as Spring beans.
 *
 * <p>Pattern: Explicit @Bean (Purist). Domain and application layers stay Spring-free. All wiring
 * happens here in the infrastructure layer.
 *
 * <p>Usage pattern (P1+):
 *
 * <pre>
 * {@literal @}Bean
 * CreateDeckUseCase createDeckUseCase(SaveDeckPort saveDeck) {
 *   return new CreateDeckService(saveDeck);
 * }
 * </pre>
 */
@Configuration
public class BeanConfiguration {
  // Use case beans wired explicitly in P1+ phases.
  // Each use case implementation gets a @Bean here, injected with its output ports.
}
