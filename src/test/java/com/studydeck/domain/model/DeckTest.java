package com.studydeck.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studydeck.domain.exception.DomainValidationException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeckTest {

  private final DeckId id = DeckId.generate();
  private final OwnerId ownerId = new OwnerId(UUID.randomUUID());

  @Test
  void createDeckWithValidArguments() {
    Deck deck =
        Deck.create(id, ownerId, "Java Fundamentals", "Core Java concepts", List.of("java"), 0.85);
    assertThat(deck.getId()).isEqualTo(id);
    assertThat(deck.getOwnerId()).isEqualTo(ownerId);
    assertThat(deck.getTitle()).isEqualTo("Java Fundamentals");
    assertThat(deck.getDescription()).isEqualTo("Core Java concepts");
    assertThat(deck.getTags()).containsExactly("java");
    assertThat(deck.getDefaultDesiredRetention()).isEqualTo(0.85);
    assertThat(deck.isArchived()).isFalse();
    assertThat(deck.getCreatedAt()).isNotNull();
    assertThat(deck.getUpdatedAt()).isNotNull();
  }

  @Test
  void createDeckWithNullDescriptionIsAllowed() {
    Deck deck = Deck.create(id, ownerId, "Java Fundamentals", null);
    assertThat(deck.getDescription()).isNull();
  }

  @Test
  void createDeckWithNullTagsDefaultsToEmptyList() {
    Deck deck = Deck.create(id, ownerId, "Java Fundamentals", null, null, 0.9);
    assertThat(deck.getTags()).isEmpty();
  }

  @Test
  void createDeckDefaultRetentionIs0dot9() {
    Deck deck = Deck.create(id, ownerId, "Java Fundamentals", null);
    assertThat(deck.getDefaultDesiredRetention()).isEqualTo(0.9);
  }

  @Test
  void blankTitleIsRejected() {
    assertThatThrownBy(() -> Deck.create(id, ownerId, "   ", null))
        .isInstanceOf(DomainValidationException.class)
        .hasMessageContaining("title");
  }

  @Test
  void emptyTitleIsRejected() {
    assertThatThrownBy(() -> Deck.create(id, ownerId, "", null))
        .isInstanceOf(DomainValidationException.class)
        .hasMessageContaining("title");
  }

  @Test
  void nullTitleIsRejected() {
    assertThatThrownBy(() -> Deck.create(id, ownerId, null, null))
        .isInstanceOf(DomainValidationException.class)
        .hasMessageContaining("title");
  }

  @Test
  void titleTooLongIsRejected() {
    String longTitle = "A".repeat(121);
    assertThatThrownBy(() -> Deck.create(id, ownerId, longTitle, null))
        .isInstanceOf(DomainValidationException.class)
        .hasMessageContaining("title");
  }

  @Test
  void descriptionTooLongIsRejected() {
    String longDesc = "X".repeat(1001);
    assertThatThrownBy(() -> Deck.create(id, ownerId, "Valid", longDesc))
        .isInstanceOf(DomainValidationException.class)
        .hasMessageContaining("description");
  }

  @Test
  void retentionBelowMinIsRejected() {
    assertThatThrownBy(() -> Deck.create(id, ownerId, "Valid", null, null, 0.69))
        .isInstanceOf(DomainValidationException.class)
        .hasMessageContaining("defaultDesiredRetention");
  }

  @Test
  void retentionAboveMaxIsRejected() {
    assertThatThrownBy(() -> Deck.create(id, ownerId, "Valid", null, null, 1.0))
        .isInstanceOf(DomainValidationException.class)
        .hasMessageContaining("defaultDesiredRetention");
  }

  @Test
  void retentionAtBoundariesIsAccepted() {
    Deck low = Deck.create(id, ownerId, "Valid", null, null, 0.70);
    assertThat(low.getDefaultDesiredRetention()).isEqualTo(0.70);

    DeckId id2 = DeckId.generate();
    Deck high = Deck.create(id2, ownerId, "Valid2", null, null, 0.99);
    assertThat(high.getDefaultDesiredRetention()).isEqualTo(0.99);
  }

  @Test
  void nullIdIsRejected() {
    assertThatThrownBy(() -> Deck.create(null, ownerId, "Valid Name", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void nullOwnerIdIsRejected() {
    assertThatThrownBy(() -> Deck.create(id, null, "Valid Name", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void archiveSetsFlagToTrue() {
    Deck deck = Deck.create(id, ownerId, "Test Deck", null);
    deck.archive();
    assertThat(deck.isArchived()).isTrue();
  }

  @Test
  void archiveUpdatesUpdatedAt() throws InterruptedException {
    Deck deck = Deck.create(id, ownerId, "Test Deck", null);
    Thread.sleep(2);
    deck.archive();
    assertThat(deck.getUpdatedAt()).isAfterOrEqualTo(deck.getCreatedAt());
  }

  @Test
  void updateChangesAllMutableFields() {
    Deck deck = Deck.create(id, ownerId, "Old Title", "Old desc", List.of("old"), 0.9);
    deck.update("New Title", "New desc", List.of("new"), 0.85);

    assertThat(deck.getTitle()).isEqualTo("New Title");
    assertThat(deck.getDescription()).isEqualTo("New desc");
    assertThat(deck.getTags()).containsExactly("new");
    assertThat(deck.getDefaultDesiredRetention()).isEqualTo(0.85);
  }

  @Test
  void retitleChangesTitle() {
    Deck deck = Deck.create(id, ownerId, "Old Title", null);
    deck.retitle("New Title");
    assertThat(deck.getTitle()).isEqualTo("New Title");
  }

  @Test
  void tagsListIsImmutable() {
    Deck deck = Deck.create(id, ownerId, "Test Deck", null, List.of("java", "spring"), 0.9);
    assertThatThrownBy(() -> deck.getTags().add("extra"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
