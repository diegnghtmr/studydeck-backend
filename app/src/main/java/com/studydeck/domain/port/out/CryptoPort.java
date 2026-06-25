package com.studydeck.domain.port.out;

/**
 * Output port for symmetric encryption/decryption of sensitive strings.
 *
 * <p>Pure Java interface — no Spring, no Jakarta imports (ArchUnit enforces domain layer purity).
 */
public interface CryptoPort {

  /**
   * Encrypts the given plaintext string.
   *
   * @param plaintext the value to encrypt; must not be null
   * @return an opaque ciphertext string (Base64-encoded in the AES-GCM adapter)
   * @throws CryptoUnavailableException if encryption is not configured
   */
  String encrypt(String plaintext);

  /**
   * Decrypts the given ciphertext string.
   *
   * @param ciphertext the value produced by {@link #encrypt}; must not be null
   * @return the original plaintext
   * @throws CryptoUnavailableException if decryption is not configured
   */
  String decrypt(String ciphertext);

  /**
   * Thrown when the crypto infrastructure is not configured (e.g. master key absent).
   *
   * <p>This is a typed error — callers can distinguish "feature unavailable" from "wrong ciphertext
   * / bad tag" (which throws standard JCA exceptions).
   */
  class CryptoUnavailableException extends RuntimeException {

    public CryptoUnavailableException(String message) {
      super(message);
    }
  }
}
