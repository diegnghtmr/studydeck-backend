package com.studydeck.infrastructure.adapter.out.crypto;

import com.studydeck.domain.port.out.CryptoPort;

/**
 * No-op {@link CryptoPort} wired when no master key is configured.
 *
 * <p>Both methods throw {@link CryptoUnavailableException}; plaintext is NEVER stored or returned.
 * This makes the provider-storage feature explicitly unavailable rather than silently degrading.
 *
 * <p>Wired by {@link com.studydeck.infrastructure.config.EncryptionConfiguration} when {@code
 * studydeck.security.encryption.master-key} is blank.
 */
public class DisabledCryptoAdapter implements CryptoPort {

  @Override
  public String encrypt(String plaintext) {
    throw new CryptoUnavailableException("encryption not configured: master key is absent");
  }

  @Override
  public String decrypt(String ciphertext) {
    throw new CryptoUnavailableException("encryption not configured: master key is absent");
  }
}
