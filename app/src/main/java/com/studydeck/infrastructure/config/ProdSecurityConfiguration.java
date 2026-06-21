package com.studydeck.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Production security guard — fails fast when no real OIDC issuer is configured.
 *
 * <p>Under the {@code prod} profile the dev HS256 {@link DevSecurityConfiguration} decoder is
 * disabled ({@code @Profile("!prod")}). Spring Boot's OAuth2 resource server then builds a real
 * {@code JwtDecoder} from {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}. If that
 * value is missing the application has no usable decoder; rather than boot in an ambiguous state we
 * refuse to start with a clear, actionable error.
 *
 * <p>This closes the silent fallback where an empty {@code issuer-uri} in prod would previously let
 * the dev HS256 decoder — keyed with a default secret committed to source — authenticate production
 * traffic.
 */
@Configuration
@Profile("prod")
public class ProdSecurityConfiguration {

  private final String issuerUri;

  public ProdSecurityConfiguration(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri) {
    if (issuerUri == null || issuerUri.isBlank()) {
      throw new IllegalStateException(
          "Production startup refused: SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI must be"
              + " set to a real OIDC issuer. The dev HS256 JWT decoder is disabled under the prod"
              + " profile and will never authenticate production traffic.");
    }
    this.issuerUri = issuerUri;
  }

  /** The validated, non-blank issuer URI active for this prod context. */
  public String issuerUri() {
    return issuerUri;
  }
}
