package com.studydeck.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.studydeck.domain.port.in.DeleteUserAiProviderUseCase;
import com.studydeck.domain.port.in.GetActiveUserAiProviderQuery;
import com.studydeck.domain.port.in.ListUserAiProvidersQuery;
import com.studydeck.domain.port.in.SaveUserAiProviderUseCase;
import com.studydeck.domain.port.out.CryptoPort;
import com.studydeck.domain.port.out.UserAiProviderRepository;
import com.studydeck.infrastructure.adapter.out.crypto.AesGcmCryptoAdapter;
import com.studydeck.integration.AiTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Spring context slice test for UserAiProvider wiring.
 *
 * <p>Verifies all four input-port beans are non-null, repository bean is present, and CryptoPort is
 * AesGcmCryptoAdapter when a valid key is set.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Import(AiTestConfiguration.class)
class UserAiProviderConfigurationTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg17")
          .withDatabaseName("studydeck_config_test")
          .withUsername("studydeck")
          .withPassword("studydeck");

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    // Valid 32-byte Base64 key
    registry.add(
        "studydeck.security.encryption.master-key",
        () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
  }

  @Autowired SaveUserAiProviderUseCase saveUserAiProviderUseCase;
  @Autowired ListUserAiProvidersQuery listUserAiProvidersQuery;
  @Autowired DeleteUserAiProviderUseCase deleteUserAiProviderUseCase;
  @Autowired GetActiveUserAiProviderQuery getActiveUserAiProviderQuery;
  @Autowired UserAiProviderRepository userAiProviderRepository;
  @Autowired CryptoPort cryptoPort;

  @Test
  void allFourInputPortBeansAreNonNull() {
    assertThat(saveUserAiProviderUseCase).isNotNull();
    assertThat(listUserAiProvidersQuery).isNotNull();
    assertThat(deleteUserAiProviderUseCase).isNotNull();
    assertThat(getActiveUserAiProviderQuery).isNotNull();
  }

  @Test
  void userAiProviderRepositoryBeanIsNonNull() {
    assertThat(userAiProviderRepository).isNotNull();
  }

  @Test
  void cryptoPortIsAesGcmCryptoAdapterWithValidKey() {
    assertThat(cryptoPort).isInstanceOf(AesGcmCryptoAdapter.class);
  }
}
