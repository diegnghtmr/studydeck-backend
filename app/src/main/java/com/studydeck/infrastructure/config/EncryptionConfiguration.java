package com.studydeck.infrastructure.config;

import com.studydeck.domain.port.out.CryptoPort;
import com.studydeck.infrastructure.adapter.out.crypto.AesGcmCryptoAdapter;
import com.studydeck.infrastructure.adapter.out.crypto.DisabledCryptoAdapter;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link CryptoPort} bean.
 *
 * <p>C1 — PROFILE-INDEPENDENT fail-fast:
 *
 * <ul>
 *   <li>Non-blank key present: Base64-decode; must be exactly 32 bytes → {@link
 *       AesGcmCryptoAdapter}. If the decoded length is not 32, throws {@link IllegalStateException}
 *       at context refresh regardless of the active Spring profile.
 *   <li>Blank key: wire {@link DisabledCryptoAdapter} — feature is unavailable, never stores
 *       plaintext, never weakens any other path.
 * </ul>
 *
 * <p>Rationale: a profile-bound guard is wrong because {@code compose.idp.yaml} does NOT set {@code
 * SPRING_PROFILES_ACTIVE=prod}. Validation is keyed ONLY on key presence/validity.
 */
@Configuration
public class EncryptionConfiguration {

  @Bean
  public CryptoPort cryptoPort(
      @Value("${studydeck.security.encryption.master-key:}") String masterKeyB64) {

    if (masterKeyB64 == null || masterKeyB64.isBlank()) {
      return new DisabledCryptoAdapter();
    }

    byte[] keyBytes;
    try {
      keyBytes = Base64.getDecoder().decode(masterKeyB64.strip());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "studydeck.security.encryption.master-key is not valid Base64: " + e.getMessage(), e);
    }

    if (keyBytes.length != 32) {
      throw new IllegalStateException(
          "studydeck.security.encryption.master-key must decode to exactly 32 bytes "
              + "(AES-256); got "
              + keyBytes.length
              + " bytes. Ensure the environment variable contains a valid 32-byte Base64 value.");
    }

    return new AesGcmCryptoAdapter(keyBytes);
  }
}
