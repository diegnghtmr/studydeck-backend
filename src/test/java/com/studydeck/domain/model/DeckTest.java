package com.studydeck.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studydeck.domain.exception.DomainValidationException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeckTest {

  private final DeckId id = DeckId.generate();
  private final OwnerId ownerId = new OwnerId(UUID.randomUUID());

  @Test
  void createDeckWithValidArguments() {
    Deck deck = Deck.create(id, ownerId, "Java Fundamentals", "Core Java concepts");
    assertThat(deck.getId()).isEqualTo(id);
    assertThat(deck.getOwnerId()).isEqualTo(ownerId);
    assertThat(deck.getName()).isEqualTo("Java Fundamentals");
    assertThat(deck.getDescription()).isEqualTo("Core Java concepts");
    assertThat(deck.isArchived()).isFalse();
    assertThat(deck.getCreatedAt()).isNotNull();
  }

  @Test
  void createDeckWithNullDescriptionIsAllowed() {
    Deck deck = Deck.create(id, ownerId, "Java Fundamentals", null);
    assertThat(deck.getDescription()).isNull();
  }

  @Test
  void blankNameIsRejected() {
    assertThatThrownBy(() -> Deck.create(id, ownerId, "   ", null))
        .isInstanceOf(DomainValidationException.class)
        .hasMessageContaining("name");
  }

  @Test
  void emptyNameIsRejected() {
    assertThatThrownBy(() -> Deck.create(id, ownerId, "", null))
        .isInstanceOf(DomainValidationException.class)
        .hasMessageContaining("name");
  }

  @Test
  void nullNameIsRejected() {
    assertThatThrownBy(() -> Deck.create(id, ownerId, null, null))
        .isInstanceOf(DomainValidationException.class)
        .hasMessageContaining("name");
  }

  @Test
  void nameTooLongIsRejected() {
    String longName = "A".repeat(201);
    assertThatThrownBy(() -> Deck.create(id, ownerId, longName, null))
        .isInstanceOf(DomainValidationException.class)
        .hasMessageContaining("name");
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
  void visibility_defaultIsPrivate() {
    Deck deck = Deck.create(id, ownerId, "Test Deck", null);
    assertThat(deck.getVisibility()).isEqualTo(Visibility.PRIVATE);
  }
}
