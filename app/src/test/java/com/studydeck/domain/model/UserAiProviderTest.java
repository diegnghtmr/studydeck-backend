package com.studydeck.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link UserAiProvider}. */
class UserAiProviderTest {

  private static final OwnerId OWNER = OwnerId.generate();
  private static final Instant NOW = Instant.now();

  private UserAiProvider validProvider() {
    return UserAiProvider.create(
        UserAiProviderId.generate(),
        OWNER,
        "My Provider",
        "https://api.example.com",
        "gpt-4o",
        "ciphertext-abc",
        "sk-o...1234",
        false,
        NOW,
        NOW);
  }

  @Test
  void create_withValidArgs_succeeds() {
    UserAiProvider p = validProvider();
    assertThat(p.getLabel()).isEqualTo("My Provider");
    assertThat(p.getBaseUrl()).isEqualTo("https://api.example.com");
    assertThat(p.getModel()).isEqualTo("gpt-4o");
    assertThat(p.getApiKeyCiphertext()).isEqualTo("ciphertext-abc");
    assertThat(p.getKeyHint()).isEqualTo("sk-o...1234");
    assertThat(p.isActive()).isFalse();
  }

  @Test
  void create_blankLabel_throws() {
    assertThatThrownBy(
            () ->
                UserAiProvider.create(
                    UserAiProviderId.generate(),
                    OWNER,
                    "   ",
                    "https://api.example.com",
                    "gpt-4o",
                    "ct",
                    "hint",
                    false,
                    NOW,
                    NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void create_blankBaseUrl_throws() {
    assertThatThrownBy(
            () ->
                UserAiProvider.create(
                    UserAiProviderId.generate(),
                    OWNER,
                    "Label",
                    "",
                    "gpt-4o",
                    "ct",
                    "hint",
                    false,
                    NOW,
                    NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void create_blankModel_throws() {
    assertThatThrownBy(
            () ->
                UserAiProvider.create(
                    UserAiProviderId.generate(),
                    OWNER,
                    "Label",
                    "https://api.example.com",
                    "",
                    "ct",
                    "hint",
                    false,
                    NOW,
                    NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void activate_setsActiveToTrue() {
    UserAiProvider p = validProvider();
    assertThat(p.isActive()).isFalse();
    p.activate();
    assertThat(p.isActive()).isTrue();
  }

  @Test
  void deactivate_setsActiveToFalse() {
    UserAiProvider p =
        UserAiProvider.create(
            UserAiProviderId.generate(),
            OWNER,
            "Label",
            "https://api.example.com",
            "gpt-4o",
            "ct",
            "hint",
            true,
            NOW,
            NOW);
    assertThat(p.isActive()).isTrue();
    p.deactivate();
    assertThat(p.isActive()).isFalse();
  }

  @Test
  void withCiphertext_returnsSameInstanceWithUpdatedFields() {
    UserAiProvider original = validProvider();
    UserAiProvider updated = original.withCiphertext("new-ciphertext", "sk-n...5678");

    // withCiphertext returns the same mutable instance updated
    assertThat(updated.getApiKeyCiphertext()).isEqualTo("new-ciphertext");
    assertThat(updated.getKeyHint()).isEqualTo("sk-n...5678");
    // Original is the same reference (mutable aggregate)
    assertThat(original).isSameAs(updated);
  }

  @Test
  void withCiphertext_ciphertextFieldStoredExactlyAsProvided() {
    UserAiProvider p = validProvider();
    p.withCiphertext("stored-ciphertext-value", "sk-s...test");
    assertThat(p.getApiKeyCiphertext()).isEqualTo("stored-ciphertext-value");
  }

  @Test
  void ownerId_isPreserved() {
    UserAiProvider p = validProvider();
    assertThat(p.getOwnerId()).isEqualTo(OWNER);
  }

  @Test
  void id_isPreserved() {
    UserAiProviderId id = UserAiProviderId.generate();
    UserAiProvider p =
        UserAiProvider.create(id, OWNER, "L", "https://x.com", "m", "ct", "hint", false, NOW, NOW);
    assertThat(p.getId()).isEqualTo(id);
  }
}
