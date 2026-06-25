package com.studydeck.infrastructure.adapter.out.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AesGcmCryptoAdapter}. */
class AesGcmCryptoAdapterTest {

  private static final byte[] VALID_KEY_32 = new byte[32]; // all-zeros, valid length

  @Test
  void roundTrip_encryptThenDecrypt_returnsOriginalPlaintext() throws Exception {
    AesGcmCryptoAdapter adapter = new AesGcmCryptoAdapter(VALID_KEY_32);
    String plaintext = "sk-super-secret-api-key-12345";

    String ciphertext = adapter.encrypt(plaintext);
    String decrypted = adapter.decrypt(ciphertext);

    assertThat(decrypted).isEqualTo(plaintext);
  }

  @Test
  void encrypt_twiceWithSamePlaintext_producesDifferentCiphertexts() throws Exception {
    AesGcmCryptoAdapter adapter = new AesGcmCryptoAdapter(VALID_KEY_32);
    String plaintext = "same-key";

    String ct1 = adapter.encrypt(plaintext);
    String ct2 = adapter.encrypt(plaintext);

    assertThat(ct1).isNotEqualTo(ct2); // distinct random IVs
  }

  @Test
  void decrypt_tamperedCiphertext_throws() throws Exception {
    AesGcmCryptoAdapter adapter = new AesGcmCryptoAdapter(VALID_KEY_32);
    String ct = adapter.encrypt("original");

    // Flip the last byte of the Base64 payload to simulate tampering
    byte[] raw = Base64.getDecoder().decode(ct);
    raw[raw.length - 1] ^= 0xFF;
    String tampered = Base64.getEncoder().encodeToString(raw);

    assertThatThrownBy(() -> adapter.decrypt(tampered)).isInstanceOf(Exception.class);
  }

  @Test
  void constructor_keyNotExactly32Bytes_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> new AesGcmCryptoAdapter(new byte[16]))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> new AesGcmCryptoAdapter(new byte[31]))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> new AesGcmCryptoAdapter(new byte[0]))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
