package com.studydeck.infrastructure.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
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
        .cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth
                    // Actuator probes + Prometheus scrape — open for load balancers and the
                    // metrics collector. The metrics endpoint exposes no PII; in production keep
                    // the actuator port reachable only from the internal monitoring network.
                    .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus")
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
   * CORS configuration source for the SPA.
   *
   * <p>The browser-based SPA runs on a different origin than the API, so it requires explicit CORS
   * allowances for its cross-origin {@code fetch}/{@code XMLHttpRequest} calls (including the
   * {@code OPTIONS} preflight). Allowed origins are externalized via {@code
   * studydeck.security.cors.allowed-origins} (comma-separated) so dev defaults to the local Vite
   * server while production sets its real origin via {@code
   * STUDYDECK_SECURITY_CORS_ALLOWED_ORIGINS} — never hardcoded.
   *
   * <p>Authentication uses bearer tokens in the {@code Authorization} header (not cookies), so
   * {@code allowCredentials} stays {@code false} and origins are matched exactly rather than via a
   * wildcard.
   */
  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${studydeck.security.cors.allowed-origins:http://localhost:5173}")
          List<String> allowedOrigins) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(allowedOrigins);
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
    config.setExposedHeaders(List.of(HttpHeaders.LOCATION));
    config.setAllowCredentials(false);
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
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
