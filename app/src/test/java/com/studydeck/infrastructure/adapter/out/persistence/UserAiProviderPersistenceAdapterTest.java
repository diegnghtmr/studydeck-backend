package com.studydeck.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.UserAiProvider;
import com.studydeck.domain.model.UserAiProviderId;
import com.studydeck.domain.port.out.UserAiProviderRepository;
import com.studydeck.integration.AiTestConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for {@link UserAiProviderPersistenceAdapter}.
 *
 * <p>Covers A-1 (DB constraints) and A-4 (adapter behaviour).
 */
@Import(AiTestConfiguration.class)
@SpringBootTest
@Testcontainers
@Transactional
class UserAiProviderPersistenceAdapterTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg17")
          .withDatabaseName("studydeck_ai_provider_test")
          .withUsername("studydeck")
          .withPassword("studydeck");

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    // Provide a valid 32-byte Base64 key so EncryptionConfiguration wires AesGcmCryptoAdapter
    registry.add(
        "studydeck.security.encryption.master-key",
        () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
  }

  @Autowired private UserAiProviderRepository repository;
  @Autowired private jakarta.persistence.EntityManager em;

  private final OwnerId alice = OwnerId.generate();
  private final OwnerId bob = OwnerId.generate();

  @BeforeEach
  void insertUsers() {
    insertUser(alice.value());
    insertUser(bob.value());
  }

  private void insertUser(UUID userId) {
    em.createNativeQuery(
            "INSERT INTO user_account(id, email, display_name) VALUES (:id, :email, :name)"
                + " ON CONFLICT DO NOTHING")
        .setParameter("id", userId)
        .setParameter("email", userId + "@test.com")
        .setParameter("name", "Test User")
        .executeUpdate();
  }

  private UserAiProvider buildProvider(OwnerId owner, String label, boolean active) {
    return UserAiProvider.create(
        UserAiProviderId.generate(),
        owner,
        label,
        "https://api.example.com",
        "gpt-4o",
        "ciphertext-placeholder",
        "sk-o...1234",
        active,
        Instant.now(),
        Instant.now());
  }

  // -----------------------------------------------------------------------
  // A-1: Partial unique index — at most one active per owner
  // -----------------------------------------------------------------------

  @Nested
  @DisplayName("A-1: partial unique index enforcement")
  class PartialUniqueIndex {

    @Test
    @DisplayName("second active row for same owner is rejected by DB constraint")
    void secondActiveRowRejectedByDb() {
      UserAiProvider first = buildProvider(alice, "Provider A", true);
      repository.save(first);
      em.flush();

      // Bypass app layer: native SQL insert of a second active row for same owner
      assertThatThrownBy(
              () -> {
                em.createNativeQuery(
                        "INSERT INTO user_ai_provider"
                            + "(id, owner_id, label, base_url, model, api_key_ciphertext, key_hint, active)"
                            + " VALUES (gen_random_uuid(), :owner, 'B', 'https://b.com', 'gpt-4', 'ct2', 'hint2', true)")
                    .setParameter("owner", alice.value())
                    .executeUpdate();
                em.flush();
              })
          .isInstanceOf(Exception.class); // DataIntegrityViolationException or PersistenceException
    }

    @Test
    @DisplayName("two inactive rows for same owner are accepted")
    void twoInactiveRowsAllowed() {
      repository.save(buildProvider(alice, "A", false));
      repository.save(buildProvider(alice, "B", false));
      em.flush();

      List<UserAiProvider> all = repository.findAllByOwner(alice);
      assertThat(all).hasSize(2);
    }
  }

  // -----------------------------------------------------------------------
  // A-1: ON DELETE CASCADE from user_account
  // -----------------------------------------------------------------------

  @Nested
  @DisplayName("A-1: ON DELETE CASCADE")
  class CascadeDelete {

    @Test
    @DisplayName("deleting user_account removes all its ai-provider rows")
    void cascadeDeleteRemovesProviderRows() {
      repository.save(buildProvider(alice, "A", false));
      repository.save(buildProvider(alice, "B", false));
      em.flush();

      em.createNativeQuery("DELETE FROM user_account WHERE id = :id")
          .setParameter("id", alice.value())
          .executeUpdate();
      em.flush();
      em.clear();

      // After cascade the provider rows should be gone
      List<UserAiProvider> remaining = repository.findAllByOwner(alice);
      assertThat(remaining).isEmpty();
    }
  }

  // -----------------------------------------------------------------------
  // A-4: Adapter behaviour
  // -----------------------------------------------------------------------

  @Nested
  @DisplayName("A-4: findByIdAndOwner IDOR guard")
  class FindByIdAndOwner {

    @Test
    @DisplayName("returns empty for cross-owner id (IDOR protection)")
    void crossOwnerReturnsEmpty() {
      UserAiProvider aliceProvider = buildProvider(alice, "Alice's", false);
      repository.save(aliceProvider);
      em.flush();

      Optional<UserAiProvider> result = repository.findByIdAndOwner(aliceProvider.getId(), bob);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("returns present for correct owner")
    void correctOwnerReturnsPresent() {
      UserAiProvider p = buildProvider(alice, "My Provider", false);
      repository.save(p);
      em.flush();

      Optional<UserAiProvider> result = repository.findByIdAndOwner(p.getId(), alice);
      assertThat(result).isPresent();
      assertThat(result.get().getLabel()).isEqualTo("My Provider");
    }
  }

  @Nested
  @DisplayName("A-4: findAllByOwner returns only own rows")
  class FindAllByOwner {

    @Test
    @DisplayName("only Alice's rows returned for Alice")
    void returnsOnlyOwnRows() {
      repository.save(buildProvider(alice, "A1", false));
      repository.save(buildProvider(alice, "A2", false));
      repository.save(buildProvider(bob, "B1", false));
      em.flush();

      List<UserAiProvider> aliceRows = repository.findAllByOwner(alice);
      assertThat(aliceRows).hasSize(2);
      assertThat(aliceRows).allMatch(p -> p.getOwnerId().equals(alice));
    }
  }

  @Nested
  @DisplayName("A-4: findActiveByOwner")
  class FindActiveByOwner {

    @Test
    @DisplayName("returns the active row when one exists")
    void returnsActiveRow() {
      repository.save(buildProvider(alice, "Inactive", false));
      UserAiProvider active = buildProvider(alice, "Active", true);
      repository.save(active);
      em.flush();

      Optional<UserAiProvider> result = repository.findActiveByOwner(alice);
      assertThat(result).isPresent();
      assertThat(result.get().getLabel()).isEqualTo("Active");
    }

    @Test
    @DisplayName("returns empty when no active row")
    void returnsEmptyWhenNoActive() {
      repository.save(buildProvider(alice, "Inactive", false));
      em.flush();

      assertThat(repository.findActiveByOwner(alice)).isEmpty();
    }
  }

  @Nested
  @DisplayName("A-4: deactivateAllForOwner")
  class DeactivateAllForOwner {

    @Test
    @DisplayName("sets all rows to active=false for the given owner only")
    void deactivatesOwnerRows() {
      repository.save(buildProvider(alice, "A1", true));
      repository.save(buildProvider(alice, "A2", false));
      UserAiProvider bobActive = buildProvider(bob, "B1", true);
      repository.save(bobActive);
      em.flush();

      repository.deactivateAllForOwner(alice);
      em.flush();
      em.clear();

      assertThat(repository.findActiveByOwner(alice)).isEmpty();
      // Bob's active row must not be affected
      assertThat(repository.findActiveByOwner(bob)).isPresent();
    }
  }

  @Nested
  @DisplayName("A-4: deleteByIdAndOwner")
  class DeleteByIdAndOwner {

    @Test
    @DisplayName("removes only the owner's row, not cross-owner row")
    void deletesOwnRowOnly() {
      UserAiProvider aliceP = buildProvider(alice, "Alice", false);
      UserAiProvider bobP = buildProvider(bob, "Bob", false);
      repository.save(aliceP);
      repository.save(bobP);
      em.flush();

      // Try to delete Alice's row as Bob — should be no-op
      repository.deleteByIdAndOwner(aliceP.getId(), bob);
      em.flush();
      em.clear();

      assertThat(repository.findByIdAndOwner(aliceP.getId(), alice)).isPresent();

      // Delete own row
      repository.deleteByIdAndOwner(aliceP.getId(), alice);
      em.flush();
      em.clear();

      assertThat(repository.findByIdAndOwner(aliceP.getId(), alice)).isEmpty();
    }
  }
}
