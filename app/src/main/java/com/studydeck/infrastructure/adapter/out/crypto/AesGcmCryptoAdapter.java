package com.studydeck.infrastructure.adapter.out.crypto;

import com.studydeck.domain.port.out.CryptoPort;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM implementation of {@link CryptoPort}.
 *
 * <p>Ciphertext format: {@code Base64( IV(12 bytes) || GCM(ciphertext + 128-bit tag) )}
 *
 * <p>Design decisions (from design artifact §CRYPTO ADAPTER DECISION):
 *
 * <ul>
 *   <li>Direct JCA (not Spring Security Encryptors) for explicit, auditable IV-prefix layout.
 *   <li>Master key is provided as raw 32 bytes (no KDF/salt).
 *   <li>12-byte random IV generated per-encrypt via {@link SecureRandom}.
 *   <li>128-bit GCM authentication tag (standard).
 *   <li>Key validated to be exactly 32 bytes (AES-256) at construction time.
 * </ul>
 */
public class AesGcmCryptoAdapter implements CryptoPort {

  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int IV_LENGTH_BYTES = 12;
  private static final int GCM_TAG_BITS = 128;

  private final SecretKeySpec secretKey;
  private final SecureRandom secureRandom;

  /**
   * @param masterKey raw AES-256 key bytes; must be exactly 32 bytes
   * @throws IllegalArgumentException if the key is not exactly 32 bytes
   */
  public AesGcmCryptoAdapter(byte[] masterKey) {
    if (masterKey == null || masterKey.length != 32) {
      throw new IllegalArgumentException(
          "Master key must be exactly 32 bytes for AES-256; got "
              + (masterKey == null ? "null" : masterKey.length));
    }
    this.secretKey = new SecretKeySpec(masterKey, "AES");
    this.secureRandom = new SecureRandom();
  }

  @Override
  public String encrypt(String plaintext) {
    try {
      byte[] iv = new byte[IV_LENGTH_BYTES];
      secureRandom.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] ciphertextWithTag =
          cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

      // Output: IV (12 bytes) || ciphertext+tag
      byte[] output = new byte[IV_LENGTH_BYTES + ciphertextWithTag.length];
      System.arraycopy(iv, 0, output, 0, IV_LENGTH_BYTES);
      System.arraycopy(ciphertextWithTag, 0, output, IV_LENGTH_BYTES, ciphertextWithTag.length);

      return Base64.getEncoder().encodeToString(output);
    } catch (Exception e) {
      throw new RuntimeException("Encryption failed", e);
    }
  }

  @Override
  public String decrypt(String ciphertext) {
    try {
      byte[] raw = Base64.getDecoder().decode(ciphertext);
      if (raw.length < IV_LENGTH_BYTES) {
        throw new IllegalArgumentException("Ciphertext too short to contain IV");
      }

      byte[] iv = new byte[IV_LENGTH_BYTES];
      System.arraycopy(raw, 0, iv, 0, IV_LENGTH_BYTES);

      byte[] ciphertextWithTag = new byte[raw.length - IV_LENGTH_BYTES];
      System.arraycopy(raw, IV_LENGTH_BYTES, ciphertextWithTag, 0, ciphertextWithTag.length);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] plaintext = cipher.doFinal(ciphertextWithTag);

      return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception e) {
      // Re-throw as-is so callers can catch AEADBadTagException directly
      if (e instanceof RuntimeException) {
        throw (RuntimeException) e;
      }
      throw new RuntimeException("Decryption failed", e);
    }
  }
}
