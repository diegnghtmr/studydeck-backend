package com.studydeck.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring Security configuration for the REST API.
 *
 * <p>Architecture:
 *
 * <ul>
 *   <li>OAuth2 Resource Server validates JWT bearer tokens for all {@code /v1/**} endpoints.
 *   <li>{@code /actuator/health} and {@code /actuator/info} are publicly accessible (liveness
 *       probes).
 *   <li>Dev profile provides a static JwtDecoder via {@link DevSecurityConfiguration} for local
 *       testing without a real IdP.
 *   <li>Prod profile relies on Spring Boot auto-configuration with {@code
 *       spring.security.oauth2.resourceserver.jwt.issuer-uri} from environment.
 *   <li>JWT scopes are mapped to {@code SCOPE_*} authorities via {@link
 *       JwtGrantedAuthoritiesConverter}.
 *   <li>Session management is stateless (JWT only, no session cookies).
 * </ul>
 *
 * <p>Never hardcodes secrets. No credentials in source.
 */
@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfiguration {

  /**
   * Main security filter chain.
   *
   * <p>Protects all {@code /v1/**} endpoints; permits actuator probes and dev API docs.
   */
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtAuthenticationConverter jwtAuthConverter,
      OncePerRequestFilter jitUserProvisioningFilter)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth
                    // Actuator probes — open for load balancers
                    .requestMatchers("/actuator/health", "/actuator/info")
                    .permitAll()
                    // Dev OpenAPI endpoint (only active in dev profile)
                    .requestMatchers("/v3/api-docs.yaml")
                    .permitAll()
                    // All v1 endpoints require authentication
                    .requestMatchers("/v1/**")
                    .authenticated()
                    // MCP transport endpoint requires authentication
                    .requestMatchers("/mcp", "/mcp/**")
                    .authenticated()
                    // Everything else — deny by default
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter)))
        .addFilterAfter(jitUserProvisioningFilter, BearerTokenAuthenticationFilter.class);
    return http.build();
  }

  /**
   * JWT authentication converter — maps JWT scopes to Spring Security SCOPE_* authorities.
   *
   * <p>Scopes from the {@code scope} claim become {@code SCOPE_study.read}, {@code
   * SCOPE_study.write}, etc. This is the standard Spring Security OAuth2 resource server pattern.
   */
  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    var grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
    grantedAuthoritiesConverter.setAuthorityPrefix("SCOPE_");
    grantedAuthoritiesConverter.setAuthoritiesClaimName("scope");

    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
    return converter;
  }
}
