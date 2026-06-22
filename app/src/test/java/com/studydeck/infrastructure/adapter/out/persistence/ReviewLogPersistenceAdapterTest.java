package com.studydeck.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.CardState;
import com.studydeck.domain.model.Deck;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.ReviewLog;
import com.studydeck.domain.model.ReviewRating;
import com.studydeck.domain.port.out.DeckRepository;
import com.studydeck.domain.port.out.ReviewLogRepository;
import com.studydeck.integration.AiTestConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * Integration test for {@link ReviewLogPersistenceAdapter}.
 *
 * <p>Focuses on {@code distinctReviewDays}, the global-stats query whose {@code ::date} projection
 * must map to {@link java.time.LocalDate}. This guards against the type-conversion regression where
 * the JPA method declared {@code List<java.sql.Date>} and Spring Data failed to convert the
 * Hibernate-produced {@code List<LocalDate>} at runtime (HTTP 500 on {@code GET /v1/stats}). Unit
 * tests using in-memory doubles cannot catch this — only a real database round-trip can.
 */
@Import(AiTestConfiguration.class)
@SpringBootTest
@Testcontainers
@Transactional
class ReviewLogPersistenceAdapterTest {

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

  @Autowired private ReviewLogRepository reviewLogRepository;
  @Autowired private DeckRepository deckRepository;
  @Autowired private jakarta.persistence.EntityManager em;

  private final OwnerId owner = OwnerId.generate();
  private CardId cardId;

  @BeforeEach
  void seedGraph() {
    insertUser(owner.value());

    DeckId deckId = DeckId.generate();
    deckRepository.save(Deck.create(deckId, owner, "Stats Deck", null));

    UUID noteId = UUID.randomUUID();
    cardId = CardId.generate();
    em.createNativeQuery(
            "INSERT INTO note(id, deck_id, note_type, content) "
                + "VALUES (:id, :deckId, 'BASIC', '{}'::jsonb)")
        .setParameter("id", noteId)
        .setParameter("deckId", deckId.value())
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO card(id, note_id, note_type, card_variant, prompt_payload, answer_payload) "
                + "VALUES (:id, :noteId, 'BASIC', 'FRONT', '{}'::jsonb, '{}'::jsonb)")
        .setParameter("id", cardId.value())
        .setParameter("noteId", noteId)
        .executeUpdate();
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

  private void appendReview(Instant reviewedAt) {
    ReviewLog log =
        new ReviewLog(cardId, ReviewRating.GOOD, CardState.REVIEW, reviewedAt, 1, 5, null);
    reviewLogRepository.save(owner, null, log);
    em.flush();
  }

  @Test
  @DisplayName("distinctReviewDays returns LocalDate values without a conversion failure")
  void distinctReviewDays_mapsToLocalDate() {
    Instant now = Instant.parse("2026-06-21T10:00:00Z");
    appendReview(now);
    appendReview(now.minus(Duration.ofHours(2))); // same UTC day → collapses to one date
    appendReview(now.minus(Duration.ofDays(2))); // distinct earlier day

    List<LocalDate> days = reviewLogRepository.distinctReviewDays(owner, ZoneId.of("UTC"));

    assertThat(days).containsExactly(LocalDate.of(2026, 6, 21), LocalDate.of(2026, 6, 19));
  }

  @Test
  @DisplayName("distinctReviewDays returns an empty list when the owner has no reviews")
  void distinctReviewDays_emptyWhenNoReviews() {
    List<LocalDate> days = reviewLogRepository.distinctReviewDays(owner, ZoneId.of("UTC"));

    assertThat(days).isEmpty();
  }
}
