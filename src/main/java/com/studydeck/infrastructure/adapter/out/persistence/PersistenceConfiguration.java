package com.studydeck.infrastructure.adapter.out.persistence;

import com.studydeck.domain.port.out.AuditEventPort;
import com.studydeck.domain.port.out.CardRepository;
import com.studydeck.domain.port.out.ClockPort;
import com.studydeck.domain.port.out.DeckRepository;
import com.studydeck.domain.port.out.IdGenerator;
import com.studydeck.domain.port.out.NoteRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Wires persistence adapters and production implementations of output ports.
 *
 * <p>Lives in the same package as the adapters so it can access package-private classes. This
 * pattern keeps the adapter implementations hidden behind the port interfaces.
 *
 * <p>Spring Data JPA repositories are discovered via @EnableJpaRepositories pointing at this
 * package.
 */
@Configuration
@EnableJpaRepositories(basePackageClasses = PersistenceConfiguration.class)
public class PersistenceConfiguration {

  @Bean
  PersistenceMapper persistenceMapper() {
    return new PersistenceMapper();
  }

  @Bean
  DeckRepository deckRepository(DeckJpaRepository jpaRepo, PersistenceMapper mapper) {
    return new DeckPersistenceAdapter(jpaRepo, mapper);
  }

  @Bean
  NoteRepository noteRepository(NoteJpaRepository jpaRepo, PersistenceMapper mapper) {
    return new NotePersistenceAdapter(jpaRepo, mapper);
  }

  @Bean
  CardRepository cardRepository(CardJpaRepository jpaRepo, PersistenceMapper mapper) {
    return new CardPersistenceAdapter(jpaRepo, mapper);
  }

  @Bean
  AuditEventPort auditEventPort(AuditEventJpaRepository jpaRepo) {
    return new AuditEventPersistenceAdapter(jpaRepo);
  }

  @Bean
  IdGenerator idGenerator() {
    return new UuidIdGenerator();
  }

  @Bean
  ClockPort clockPort() {
    return new SystemClockPort();
  }
}
