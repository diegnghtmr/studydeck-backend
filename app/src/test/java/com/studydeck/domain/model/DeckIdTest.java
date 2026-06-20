package com.studydeck.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeckIdTest {

  @Test
  void generateProducesNonNullId() {
    DeckId id = DeckId.generate();
    assertThat(id).isNotNull();
    assertThat(id.value()).isNotNull();
  }

  @Test
  void twoGeneratedIdsAreDistinct() {
    assertThat(DeckId.generate()).isNotEqualTo(DeckId.generate());
  }

  @Test
  void constructionFromUuidWorks() {
    UUID uuid = UUID.randomUUID();
    DeckId id = new DeckId(uuid);
    assertThat(id.value()).isEqualTo(uuid);
  }

  @Test
  void nullValueIsRejected() {
    assertThatThrownBy(() -> new DeckId(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void equalityIsValueBased() {
    UUID uuid = UUID.randomUUID();
    assertThat(new DeckId(uuid)).isEqualTo(new DeckId(uuid));
  }
}
