package com.studydeck.infrastructure.config;

import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Dev-only security configuration that provides a static JwtDecoder backed by an HMAC-SHA256 key.
 *
 * <p>This allows {@code docker compose up} to work end-to-end without a real IdP (Keycloak). A
 * static bearer token signed with the dev secret can be generated using the provided utility.
 *
 * <p>NEVER use this in production. The {@code @ConditionalOnMissingBean(JwtDecoder.class)}
 * annotation ensures this bean is NOT loaded when a real JwtDecoder is already configured (e.g.,
 * via {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} in production).
 *
 * <p>Dev token generation example (jwtcli or any JWT library):
 *
 * <pre>
 *   Header: { "alg": "HS256" }
 *   Payload: {
 *     "sub": "00000000-0000-0000-0000-000000000001",
 *     "email": "dev@studydeck.local",
 *     "scope": "study.read study.write review.write",
 *     "iat": &lt;now&gt;,
 *     "exp": &lt;now + 86400&gt;
 *   }
 *   Signed with: DEV_JWT_SECRET (from .env or environment variable)
 * </pre>
 *
 * <p>The secret MUST be set via the environment variable {@code DEV_JWT_SECRET} (min 32 bytes). A
 * default value is provided ONLY for local development convenience — override in your .env.
 */
@Configuration
@ConditionalOnMissingBean(JwtDecoder.class)
public class DevSecurityConfiguration {

  /**
   * Default dev secret — NOT a production credential. Override via {@code DEV_JWT_SECRET} env var.
   * Min 32 ASCII characters for HS256.
   */
  @Bean
  JwtDecoder devJwtDecoder(
      @Value("${studydeck.security.dev.jwt-secret:studydeck-dev-secret-key-32-chars-min-!!}")
          String secret) {
    byte[] keyBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
    return NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
  }
}
