package com.studydeck.infrastructure.adapter.in.web;

import com.studydeck.domain.port.in.ProvisionUserUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring configuration for the web (in) adapter layer.
 *
 * <p>Lives in the same package as the package-private adapter classes so it can instantiate them
 * and expose them as Spring beans.
 */
@Configuration
public class WebConfiguration {

  /**
   * JIT user provisioning filter — registered here (package-private access) and injected into
   * {@link com.studydeck.infrastructure.config.SecurityConfiguration} as an {@link
   * OncePerRequestFilter}.
   */
  @Bean
  OncePerRequestFilter jitUserProvisioningFilter(ProvisionUserUseCase provisionUser) {
    return new JitUserProvisioningFilter(provisionUser);
  }
}
