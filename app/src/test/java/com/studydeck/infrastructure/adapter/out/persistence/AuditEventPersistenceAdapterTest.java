package com.studydeck.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.out.AuditEventPort;
import com.studydeck.integration.AiTestConfiguration;
import java.util.List;
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

/** Integration test for {@link AuditEventPersistenceAdapter}. */
@Import(AiTestConfiguration.class)
@SpringBootTest
@Testcontainers
@Transactional
class AuditEventPersistenceAdapterTest {

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

  @Autowired private AuditEventPort auditEventPort;

  @Autowired private AuditEventJpaRepository jpaRepo;

  private final OwnerId alice = OwnerId.generate();

  @Test
  @DisplayName("records an audit event and it is persisted")
  void recordsAuditEvent() {
    auditEventPort.record(alice, "deck.created", "Deck", "some-deck-id");

    List<AuditEventJpaEntity> events = jpaRepo.findAll();
    assertThat(events).hasSize(1);
    AuditEventJpaEntity event = events.getFirst();
    assertThat(event.getActorId()).isEqualTo(alice.value());
    assertThat(event.getAction()).isEqualTo("deck.created");
    assertThat(event.getTargetType()).isEqualTo("Deck");
    assertThat(event.getTargetId()).isEqualTo("some-deck-id");
    assertThat(event.getOccurredAt()).isNotNull();
  }

  @Test
  @DisplayName("appends multiple audit events independently")
  void appendsMultipleEvents() {
    auditEventPort.record(alice, "deck.created", "Deck", "deck-1");
    auditEventPort.record(alice, "note.created", "Note", "note-1");
    auditEventPort.record(alice, "deck.deleted", "Deck", "deck-1");

    assertThat(jpaRepo.count()).isEqualTo(3);
  }

  @Test
  @DisplayName("each event gets a unique UUID id")
  void eachEventHasUniqueId() {
    auditEventPort.record(alice, "deck.created", "Deck", "deck-1");
    auditEventPort.record(alice, "deck.created", "Deck", "deck-2");

    List<AuditEventJpaEntity> events = jpaRepo.findAll();
    assertThat(events.getFirst().getId()).isNotEqualTo(events.getLast().getId());
  }
}
