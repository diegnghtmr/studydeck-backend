package com.studydeck.infrastructure.adapter.out.crypto;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studydeck.domain.port.out.CryptoPort;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link DisabledCryptoAdapter}. */
class DisabledCryptoAdapterTest {

  private final DisabledCryptoAdapter adapter = new DisabledCryptoAdapter();

  @Test
  void encrypt_throwsCryptoUnavailableException() {
    assertThatThrownBy(() -> adapter.encrypt("any plaintext"))
        .isInstanceOf(CryptoPort.CryptoUnavailableException.class);
  }

  @Test
  void decrypt_throwsCryptoUnavailableException() {
    assertThatThrownBy(() -> adapter.decrypt("any ciphertext"))
        .isInstanceOf(CryptoPort.CryptoUnavailableException.class);
  }
}
