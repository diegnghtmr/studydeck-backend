package com.studydeck.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.studydeck.domain.port.out.CryptoPort;
import com.studydeck.infrastructure.adapter.out.crypto.AesGcmCryptoAdapter;
import com.studydeck.infrastructure.adapter.out.crypto.DisabledCryptoAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Tests for {@link EncryptionConfiguration} (C1 — profile-independent fail-fast).
 *
 * <p>No {@code @ActiveProfiles} — behaviour must be consistent regardless of active profile.
 */
class EncryptionConfigurationTest {

  // A valid 32-byte key in Base64 (32 zero bytes → 44 char Base64 string)
  private static final String VALID_32_BYTE_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

  // A Base64 string that decodes to only 16 bytes (NOT 32)
  private static final String INVALID_16_BYTE_KEY = "AAAAAAAAAAAAAAAAAAAAAA==";

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(EncryptionConfiguration.class);

  @Test
  void validKey_wires_AesGcmCryptoAdapter() {
    contextRunner
        .withPropertyValues("studydeck.security.encryption.master-key=" + VALID_32_BYTE_KEY)
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(CryptoPort.class);
              assertThat(ctx.getBean(CryptoPort.class)).isInstanceOf(AesGcmCryptoAdapter.class);
            });
  }

  @Test
  void blankKey_wires_DisabledCryptoAdapter() {
    contextRunner
        .withPropertyValues("studydeck.security.encryption.master-key=")
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(CryptoPort.class);
              assertThat(ctx.getBean(CryptoPort.class)).isInstanceOf(DisabledCryptoAdapter.class);
            });
  }

  @Test
  void invalidKey_wrongLengthBase64_failsContextStartup() {
    contextRunner
        .withPropertyValues("studydeck.security.encryption.master-key=" + INVALID_16_BYTE_KEY)
        .run(
            ctx -> {
              assertThat(ctx).hasFailed();
              assertThat(ctx.getStartupFailure())
                  .isInstanceOf(BeanCreationException.class)
                  .hasRootCauseInstanceOf(IllegalStateException.class);
            });
  }
}
