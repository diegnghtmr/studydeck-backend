package com.studydeck.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link UserAiProviderId}. */
class UserAiProviderIdTest {

  @Test
  void generate_returnsNonNullId() {
    UserAiProviderId id = UserAiProviderId.generate();
    assertThat(id).isNotNull();
    assertThat(id.value()).isNotNull();
  }

  @Test
  void generate_returnsDifferentValues() {
    UserAiProviderId a = UserAiProviderId.generate();
    UserAiProviderId b = UserAiProviderId.generate();
    assertThat(a.value()).isNotEqualTo(b.value());
  }

  @Test
  void constructor_nullValueThrows() {
    assertThatThrownBy(() -> new UserAiProviderId(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void toString_returnsUuidString() {
    UUID uuid = UUID.randomUUID();
    UserAiProviderId id = new UserAiProviderId(uuid);
    assertThat(id.toString()).isEqualTo(uuid.toString());
  }
}
