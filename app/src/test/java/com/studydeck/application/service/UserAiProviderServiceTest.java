package com.studydeck.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studydeck.application.exception.NotFoundException;
import com.studydeck.domain.model.AiProviderConfig;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.UserAiProvider;
import com.studydeck.domain.model.UserAiProviderId;
import com.studydeck.domain.port.in.DeleteUserAiProviderUseCase;
import com.studydeck.domain.port.in.ListUserAiProvidersQuery;
import com.studydeck.domain.port.in.SaveUserAiProviderUseCase;
import com.studydeck.domain.port.out.CryptoPort;
import com.studydeck.domain.port.out.UserAiProviderRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Pure unit tests for {@link UserAiProviderService}. No Spring context. */
class UserAiProviderServiceTest {

  // -----------------------------------------------------------------------
  // Fakes
  // -----------------------------------------------------------------------

  /** Reversible fake CryptoPort: encrypt = "enc[plaintext]", decrypt = unpacks. */
  static class FakeCryptoPort implements CryptoPort {
    @Override
    public String encrypt(String plaintext) {
      return "enc[" + plaintext + "]";
    }

    @Override
    public String decrypt(String ciphertext) {
      if (!ciphertext.startsWith("enc[") || !ciphertext.endsWith("]")) {
        throw new IllegalArgumentException("Unknown ciphertext: " + ciphertext);
      }
      return ciphertext.substring(4, ciphertext.length() - 1);
    }
  }

  /** In-memory stub repository. */
  static class InMemoryUserAiProviderRepository implements UserAiProviderRepository {

    final List<UserAiProvider> store = new ArrayList<>();
    int deactivateAllCalls = 0;

    @Override
    public void save(UserAiProvider provider) {
      store.removeIf(p -> p.getId().equals(provider.getId()));
      store.add(provider);
    }

    @Override
    public Optional<UserAiProvider> findByIdAndOwner(UserAiProviderId id, OwnerId owner) {
      return store.stream()
          .filter(p -> p.getId().equals(id) && p.getOwnerId().equals(owner))
          .findFirst();
    }

    @Override
    public List<UserAiProvider> findAllByOwner(OwnerId owner) {
      return store.stream().filter(p -> p.getOwnerId().equals(owner)).toList();
    }

    @Override
    public Optional<UserAiProvider> findActiveByOwner(OwnerId owner) {
      return store.stream().filter(p -> p.getOwnerId().equals(owner) && p.isActive()).findFirst();
    }

    @Override
    public void deleteByIdAndOwner(UserAiProviderId id, OwnerId owner) {
      store.removeIf(p -> p.getId().equals(id) && p.getOwnerId().equals(owner));
    }

    @Override
    public void deactivateAllForOwner(OwnerId owner) {
      deactivateAllCalls++;
      store.stream().filter(p -> p.getOwnerId().equals(owner)).forEach(UserAiProvider::deactivate);
    }
  }

  /** Fake IdGenerator that increments a counter for determinism. */
  static class SequentialIdGenerator implements com.studydeck.domain.port.out.IdGenerator {
    private final AtomicInteger counter = new AtomicInteger(1);

    @Override
    public UUID generate() {
      return new UUID(0L, counter.getAndIncrement());
    }
  }

  /** Fake ClockPort fixed at a specific instant. */
  static class FixedClockPort implements com.studydeck.domain.port.out.ClockPort {
    private final Instant instant = Instant.parse("2026-01-01T00:00:00Z");

    @Override
    public Instant now() {
      return instant;
    }
  }

  // -----------------------------------------------------------------------
  // Setup
  // -----------------------------------------------------------------------

  private InMemoryUserAiProviderRepository repo;
  private FakeCryptoPort crypto;
  private UserAiProviderService service;
  private final OwnerId owner = OwnerId.generate();
  private final OwnerId otherOwner = OwnerId.generate();

  @BeforeEach
  void setUp() {
    repo = new InMemoryUserAiProviderRepository();
    crypto = new FakeCryptoPort();
    service =
        new UserAiProviderService(repo, crypto, new SequentialIdGenerator(), new FixedClockPort());
  }

  // -----------------------------------------------------------------------
  // SAVE — create
  // -----------------------------------------------------------------------

  @Nested
  @DisplayName("SAVE — create")
  class SaveCreate {

    @Test
    @DisplayName("encrypts key, computes keyHint, persists no plaintext field")
    void save_encryptsKeyAndComputesHint() {
      var cmd =
          new SaveUserAiProviderUseCase.Command(
              owner,
              null,
              "My Provider",
              "https://api.example.com",
              "gpt-4o",
              "sk-openai1234",
              false);

      SaveUserAiProviderUseCase.Result result = service.save(cmd);

      assertThat(result).isNotNull();
      assertThat(result.keyHint()).isEqualTo("sk-o…1234"); // first4…last4
      // Stored provider must have ciphertext, not plaintext
      var stored = repo.store.stream().findFirst().orElseThrow();
      assertThat(stored.getApiKeyCiphertext()).isEqualTo("enc[sk-openai1234]");
      // Ciphertext must not be the raw plaintext (even the fake wraps it)
      assertThat(stored.getApiKeyCiphertext()).isNotEqualTo("sk-openai1234");
    }
  }

  // -----------------------------------------------------------------------
  // SAVE — update without new key
  // -----------------------------------------------------------------------

  @Nested
  @DisplayName("SAVE — update preserves existing ciphertext when no new key")
  class SaveUpdate {

    @Test
    @DisplayName("update without plaintextApiKey preserves ciphertext and hint unchanged")
    void update_withoutNewKey_preservesCiphertextAndHint() {
      // Create first
      var createCmd =
          new SaveUserAiProviderUseCase.Command(
              owner, null, "Provider A", "https://a.com", "gpt-4o", "sk-original-key123", false);
      SaveUserAiProviderUseCase.Result created = service.save(createCmd);

      var storedBefore = repo.store.stream().findFirst().orElseThrow();
      String originalCiphertext = storedBefore.getApiKeyCiphertext();
      String originalHint = storedBefore.getKeyHint();

      // Update label only (no new key)
      var updateCmd =
          new SaveUserAiProviderUseCase.Command(
              owner, created.id(), "Provider A Renamed", "https://a.com", "gpt-4o", null, false);
      service.save(updateCmd);

      var storedAfter = repo.store.stream().findFirst().orElseThrow();
      assertThat(storedAfter.getApiKeyCiphertext()).isEqualTo(originalCiphertext);
      assertThat(storedAfter.getKeyHint()).isEqualTo(originalHint);
      assertThat(storedAfter.getLabel()).isEqualTo("Provider A Renamed");
    }
  }

  // -----------------------------------------------------------------------
  // SAVE — setActive
  // -----------------------------------------------------------------------

  @Nested
  @DisplayName("SAVE — setActive calls deactivateAllForOwner then activates")
  class SaveSetActive {

    @Test
    @DisplayName("setActive=true deactivates all then activates this provider")
    void setActive_callsDeactivateAllThenActivates() {
      var cmd =
          new SaveUserAiProviderUseCase.Command(
              owner, null, "Provider", "https://a.com", "gpt-4o", "sk-somekeyhere", true);

      service.save(cmd);

      assertThat(repo.deactivateAllCalls).isEqualTo(1);
      var stored = repo.store.stream().findFirst().orElseThrow();
      assertThat(stored.isActive()).isTrue();
    }
  }

  // -----------------------------------------------------------------------
  // LIST
  // -----------------------------------------------------------------------

  @Nested
  @DisplayName("LIST — masked view, no decrypt")
  class ListProviders {

    @Test
    @DisplayName("returns masked view with keyHint, no decrypt call")
    void list_returnsMaskedView() {
      // Seed two providers in the repository directly
      var p1 =
          UserAiProvider.create(
              UserAiProviderId.generate(),
              owner,
              "Provider 1",
              "https://a.com",
              "gpt-4o",
              "enc[sk-key-one-here]",
              "sk-k…here",
              false,
              Instant.now(),
              Instant.now());
      var p2 =
          UserAiProvider.create(
              UserAiProviderId.generate(),
              owner,
              "Provider 2",
              "https://b.com",
              "claude-3",
              "enc[another-key-value]",
              "anot…alue",
              true,
              Instant.now(),
              Instant.now());
      repo.save(p1);
      repo.save(p2);

      List<ListUserAiProvidersQuery.Masked> results = service.list(owner);

      assertThat(results).hasSize(2);
      // Each result should have keyHint but NOT apiKey or ciphertext
      results.forEach(
          m -> {
            assertThat(m.keyHint()).isNotNull();
            // No apiKey field on Masked — compile-time enforced
          });
    }
  }

  // -----------------------------------------------------------------------
  // GET ACTIVE
  // -----------------------------------------------------------------------

  @Nested
  @DisplayName("GET ACTIVE — decrypts and returns AiProviderConfig")
  class GetActive {

    @Test
    @DisplayName("decrypts active provider and returns AiProviderConfig")
    void getActive_decryptsAndReturnsConfig() {
      var cmd =
          new SaveUserAiProviderUseCase.Command(
              owner, null, "My AI", "https://api.openai.com", "gpt-4o", "sk-secret-key12", true);
      service.save(cmd);

      Optional<AiProviderConfig> result = service.getActive(owner);

      assertThat(result).isPresent();
      assertThat(result.get().apiKey()).isEqualTo("sk-secret-key12");
      assertThat(result.get().baseUrl()).isEqualTo("https://api.openai.com");
      assertThat(result.get().model()).isEqualTo("gpt-4o");
    }

    @Test
    @DisplayName("returns empty when no active provider")
    void getActive_noActiveProvider_returnsEmpty() {
      var cmd =
          new SaveUserAiProviderUseCase.Command(
              owner, null, "Inactive", "https://a.com", "gpt-4o", "sk-somekey12345", false);
      service.save(cmd);

      Optional<AiProviderConfig> result = service.getActive(owner);
      assertThat(result).isEmpty();
    }
  }

  // -----------------------------------------------------------------------
  // DISABLED CRYPTO — save throws, nothing persisted
  // -----------------------------------------------------------------------

  @Nested
  @DisplayName("DisabledCryptoAdapter — save throws CryptoUnavailableException")
  class DisabledCrypto {

    @Test
    @DisplayName("save with DisabledCryptoAdapter throws and does not persist")
    void save_withDisabledCrypto_throwsAndDoesNotPersist() {
      var disabledService =
          new UserAiProviderService(
              repo,
              new com.studydeck.infrastructure.adapter.out.crypto.DisabledCryptoAdapter(),
              new SequentialIdGenerator(),
              new FixedClockPort());

      var cmd =
          new SaveUserAiProviderUseCase.Command(
              owner, null, "Provider", "https://a.com", "gpt-4o", "sk-key", false);

      assertThatThrownBy(() -> disabledService.save(cmd))
          .isInstanceOf(CryptoPort.CryptoUnavailableException.class);

      // Nothing persisted
      assertThat(repo.store).isEmpty();
    }
  }

  // -----------------------------------------------------------------------
  // IDOR — cross-owner findByIdAndOwner returns empty → NotFoundException
  // -----------------------------------------------------------------------

  @Nested
  @DisplayName("IDOR guard — cross-owner returns NotFoundException")
  class IdorGuard {

    @Test
    @DisplayName("delete with wrong owner throws NotFoundException")
    void delete_crossOwner_throwsNotFoundException() {
      var cmd =
          new SaveUserAiProviderUseCase.Command(
              owner, null, "Provider", "https://a.com", "gpt-4o", "sk-somekey12345", false);
      SaveUserAiProviderUseCase.Result created = service.save(cmd);

      var deleteCmd = new DeleteUserAiProviderUseCase.Command(otherOwner, created.id());
      assertThatThrownBy(() -> service.execute(deleteCmd)).isInstanceOf(NotFoundException.class);
    }
  }
}
