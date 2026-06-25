package com.studydeck.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.CardScheduleState;
import com.studydeck.domain.model.Deck;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.out.CardScheduleStateRepository;
import com.studydeck.domain.port.out.DeckRepository;
import com.studydeck.integration.AiTestConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * Integration test for {@link CardScheduleStatePersistenceAdapter#countNewByDeck}.
 *
 * <p>Cards with no schedule-state row are treated as NEW (backfill strategy), so the count is
 * exercised here without seeding schedule rows. Verifies the per-deck NEW count is scoped to a
 * single deck — the value behind the dashboard's per-deck "new" stat.
 */
@Import(AiTestConfiguration.class)
@SpringBootTest
@Testcontainers
@Transactional
class CardScheduleStatePersistenceAdapterTest {

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

  @Autowired private CardScheduleStateRepository scheduleStateRepository;
  @Autowired private DeckRepository deckRepository;
  @Autowired private jakarta.persistence.EntityManager em;

  private final OwnerId owner = OwnerId.generate();
  private DeckId deckA;
  private DeckId deckB;

  @BeforeEach
  void seedGraph() {
    insertUser(owner.value());
    deckA = DeckId.generate();
    deckB = DeckId.generate();
    deckRepository.save(Deck.create(deckA, owner, "Deck A", null));
    deckRepository.save(Deck.create(deckB, owner, "Deck B", null));

    // Deck A: 3 cards, Deck B: 2 cards — all NEW (no schedule rows).
    for (int i = 0; i < 3; i++) {
      insertCard(deckA);
    }
    for (int i = 0; i < 2; i++) {
      insertCard(deckB);
    }
    em.flush();
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

  private void insertCard(DeckId deckId) {
    UUID noteId = UUID.randomUUID();
    em.createNativeQuery(
            "INSERT INTO note(id, deck_id, note_type, content) "
                + "VALUES (:id, :deckId, 'BASIC', '{}'::jsonb)")
        .setParameter("id", noteId)
        .setParameter("deckId", deckId.value())
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO card(id, note_id, note_type, card_variant, prompt_payload, answer_payload) "
                + "VALUES (:id, :noteId, 'BASIC', 'FRONT', '{}'::jsonb, '{}'::jsonb)")
        .setParameter("id", UUID.randomUUID())
        .setParameter("noteId", noteId)
        .executeUpdate();
  }

  @Test
  @DisplayName("countNewByDeck counts NEW (unscheduled) cards scoped to a single deck")
  void countNewByDeck_isScopedToDeck() {
    assertThat(scheduleStateRepository.countNewByDeck(owner, deckA)).isEqualTo(3);
    assertThat(scheduleStateRepository.countNewByDeck(owner, deckB)).isEqualTo(2);
    assertThat(scheduleStateRepository.countNewGlobal(owner)).isEqualTo(5);
  }

  @Test
  @DisplayName("findDueCardIds excludes suspended cards — suspended card due now must not appear")
  void findDueCardIds_excludesSuspended() {
    // Insert a card that is suspended=TRUE and give it a due schedule row
    UUID noteId = UUID.randomUUID();
    UUID suspendedCardId = UUID.randomUUID();
    em.createNativeQuery(
            "INSERT INTO note(id, deck_id, note_type, content) "
                + "VALUES (:id, :deckId, 'BASIC', '{}'::jsonb)")
        .setParameter("id", noteId)
        .setParameter("deckId", deckA.value())
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO card(id, note_id, note_type, card_variant, prompt_payload, "
                + "answer_payload, suspended) "
                + "VALUES (:id, :noteId, 'BASIC', 'FRONT', '{}'::jsonb, '{}'::jsonb, TRUE)")
        .setParameter("id", suspendedCardId)
        .setParameter("noteId", noteId)
        .executeUpdate();

    // Insert a non-suspended card too so we verify the query returns at least something
    UUID activeNoteId = UUID.randomUUID();
    UUID activeCardId = UUID.randomUUID();
    em.createNativeQuery(
            "INSERT INTO note(id, deck_id, note_type, content) "
                + "VALUES (:id, :deckId, 'BASIC', '{}'::jsonb)")
        .setParameter("id", activeNoteId)
        .setParameter("deckId", deckA.value())
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO card(id, note_id, note_type, card_variant, prompt_payload, "
                + "answer_payload, suspended) "
                + "VALUES (:id, :noteId, 'BASIC', 'FRONT', '{}'::jsonb, '{}'::jsonb, FALSE)")
        .setParameter("id", activeCardId)
        .setParameter("noteId", activeNoteId)
        .executeUpdate();

    Instant now = Instant.now();
    // Schedule both cards as due now
    scheduleStateRepository.save(
        owner, new CardId(suspendedCardId), CardScheduleState.newFsrsCard(now));
    scheduleStateRepository.save(
        owner, new CardId(activeCardId), CardScheduleState.newFsrsCard(now));
    em.flush();
    em.clear();

    List<CardId> dueIds =
        scheduleStateRepository.findDueCardIds(owner, deckA, now.plusSeconds(1), 100);

    // The suspended card must NOT appear; the active card must appear
    assertThat(dueIds.stream().map(CardId::value)).doesNotContain(suspendedCardId);
    assertThat(dueIds.stream().map(CardId::value)).contains(activeCardId);
  }
}
