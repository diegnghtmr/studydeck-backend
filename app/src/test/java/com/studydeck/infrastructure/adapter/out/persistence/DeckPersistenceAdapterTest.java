package com.studydeck.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.studydeck.domain.model.Deck;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.out.DeckRepository;
import com.studydeck.integration.AiTestConfiguration;
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
 * Integration test for {@link DeckPersistenceAdapter}.
 *
 * <p>Uses Testcontainers with pgvector:pg17 image. Flyway migrations (V1 + V2) run automatically
 * via Spring Boot auto-configuration.
 */
@Import(AiTestConfiguration.class)
@SpringBootTest
@Testcontainers
@Transactional
class DeckPersistenceAdapterTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg17")
          .withDatabaseName("studydeck_test")
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
  }

  @Autowired private DeckRepository deckRepository;

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

  @Nested
  @DisplayName("save and findById")
  class SaveAndFind {

    @Test
    @DisplayName("saves a deck and retrieves it by id")
    void savesAndFindsById() {
      DeckId id = DeckId.generate();
      Deck deck = Deck.create(id, alice, "Java Basics", "Core concepts", List.of("java"), 0.9);

      deckRepository.save(deck);
      em.flush();
      em.clear();

      Optional<Deck> loaded = deckRepository.findById(id);
      assertThat(loaded).isPresent();
      Deck found = loaded.get();
      assertThat(found.getId()).isEqualTo(id);
      assertThat(found.getTitle()).isEqualTo("Java Basics");
      assertThat(found.getDescription()).isEqualTo("Core concepts");
      assertThat(found.getTags()).containsExactly("java");
      assertThat(found.getDefaultDesiredRetention()).isEqualTo(0.9);
      assertThat(found.isArchived()).isFalse();
      assertThat(found.getOwnerId()).isEqualTo(alice);
      assertThat(found.getCreatedAt()).isNotNull();
      assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("saves deck with multiple tags and non-default retention")
    void savesTagsAndRetention() {
      DeckId id = DeckId.generate();
      Deck deck = Deck.create(id, alice, "Spring Boot", null, List.of("java", "spring"), 0.85);

      deckRepository.save(deck);
      em.flush();
      em.clear();

      Deck loaded = deckRepository.findById(id).orElseThrow();
      assertThat(loaded.getTags()).containsExactlyInAnyOrder("java", "spring");
      assertThat(loaded.getDefaultDesiredRetention()).isEqualTo(0.85);
    }

    @Test
    @DisplayName("returns empty when deck does not exist")
    void returnsEmptyWhenNotFound() {
      Optional<Deck> result = deckRepository.findById(DeckId.generate());
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("updates an existing deck on second save")
    void updatesDeck() {
      DeckId id = DeckId.generate();
      Deck deck = Deck.create(id, alice, "Old Title", null);
      deckRepository.save(deck);
      em.flush();
      em.clear();

      deck.retitle("New Title");
      deckRepository.save(deck);
      em.flush();
      em.clear();

      Deck loaded = deckRepository.findById(id).orElseThrow();
      assertThat(loaded.getTitle()).isEqualTo("New Title");
    }

    @Test
    @DisplayName("archives a deck and round-trips the flag")
    void archivesDeck() {
      DeckId id = DeckId.generate();
      Deck deck = Deck.create(id, alice, "Deck", null);
      deckRepository.save(deck);
      em.flush();
      em.clear();

      deck.archive();
      deckRepository.save(deck);
      em.flush();
      em.clear();

      Deck loaded = deckRepository.findById(id).orElseThrow();
      assertThat(loaded.isArchived()).isTrue();
    }
  }

  @Nested
  @DisplayName("findByOwner pagination + filters")
  class FindByOwnerTests {

    @Test
    @DisplayName("returns only decks owned by the given user")
    void returnsOnlyOwnedDecks() {
      deckRepository.save(Deck.create(DeckId.generate(), alice, "Alice Deck", null));
      deckRepository.save(Deck.create(DeckId.generate(), bob, "Bob Deck", null));
      em.flush();
      em.clear();

      List<Deck> aliceDecks = deckRepository.findByOwner(alice, false, null, 0, 10);
      assertThat(aliceDecks).hasSize(1);
      assertThat(aliceDecks.getFirst().getOwnerId()).isEqualTo(alice);
    }

    @Test
    @DisplayName("excludes archived decks when includeArchived=false")
    void excludesArchivedDecks() {
      Deck active = Deck.create(DeckId.generate(), alice, "Active", null);
      Deck archived = Deck.create(DeckId.generate(), alice, "Archived", null);
      archived.archive();

      deckRepository.save(active);
      deckRepository.save(archived);
      em.flush();
      em.clear();

      List<Deck> result = deckRepository.findByOwner(alice, false, null, 0, 10);
      assertThat(result).hasSize(1);
      assertThat(result.getFirst().getTitle()).isEqualTo("Active");
    }

    @Test
    @DisplayName("includes archived decks when includeArchived=true")
    void includesArchivedDecks() {
      Deck active = Deck.create(DeckId.generate(), alice, "Active", null);
      Deck archived = Deck.create(DeckId.generate(), alice, "Archived", null);
      archived.archive();

      deckRepository.save(active);
      deckRepository.save(archived);
      em.flush();
      em.clear();

      List<Deck> result = deckRepository.findByOwner(alice, true, null, 0, 10);
      assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("filters by search term in title")
    void filtersBySearchTerm() {
      deckRepository.save(Deck.create(DeckId.generate(), alice, "Java Basics", null));
      deckRepository.save(Deck.create(DeckId.generate(), alice, "Python Advanced", null));
      em.flush();
      em.clear();

      List<Deck> result = deckRepository.findByOwner(alice, false, "java", 0, 10);
      assertThat(result).hasSize(1);
      assertThat(result.getFirst().getTitle()).isEqualTo("Java Basics");
    }

    @Test
    @DisplayName("paginates with offset and limit")
    void paginatesWithOffsetAndLimit() {
      for (int i = 0; i < 5; i++) {
        deckRepository.save(Deck.create(DeckId.generate(), alice, "Deck " + i, null));
      }
      em.flush();
      em.clear();

      List<Deck> page0 = deckRepository.findByOwner(alice, false, null, 0, 2);
      List<Deck> page1 = deckRepository.findByOwner(alice, false, null, 2, 2);

      assertThat(page0).hasSize(2);
      assertThat(page1).hasSize(2);
    }

    @Test
    @DisplayName("countByOwner returns correct total")
    void countByOwner() {
      deckRepository.save(Deck.create(DeckId.generate(), alice, "Deck 1", null));
      deckRepository.save(Deck.create(DeckId.generate(), alice, "Deck 2", null));
      em.flush();
      em.clear();

      long count = deckRepository.countByOwner(alice, false, null);
      assertThat(count).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("deleteById")
  class DeleteTests {

    @Test
    @DisplayName("deletes an existing deck")
    void deletesExistingDeck() {
      DeckId id = DeckId.generate();
      deckRepository.save(Deck.create(id, alice, "Deck", null));
      em.flush();
      em.clear();

      deckRepository.deleteById(id);
      em.flush();
      em.clear();

      assertThat(deckRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("deleteById is a no-op for non-existent id")
    void noOpForNonExistentId() {
      // Should not throw
      deckRepository.deleteById(DeckId.generate());
    }
  }
}
