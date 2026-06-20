package com.studydeck.infrastructure.config;

import com.studydeck.application.service.CardService;
import com.studydeck.application.service.DeckService;
import com.studydeck.application.service.NoteService;
import com.studydeck.domain.port.in.ArchiveDeckUseCase;
import com.studydeck.domain.port.in.CreateDeckUseCase;
import com.studydeck.domain.port.in.CreateNoteUseCase;
import com.studydeck.domain.port.in.DeleteCardUseCase;
import com.studydeck.domain.port.in.DeleteDeckUseCase;
import com.studydeck.domain.port.in.DeleteNoteUseCase;
import com.studydeck.domain.port.in.GetCardQuery;
import com.studydeck.domain.port.in.GetDeckQuery;
import com.studydeck.domain.port.in.GetNoteQuery;
import com.studydeck.domain.port.in.ListCardsForNoteQuery;
import com.studydeck.domain.port.in.ListCardsQuery;
import com.studydeck.domain.port.in.ListDecksQuery;
import com.studydeck.domain.port.in.ListNotesQuery;
import com.studydeck.domain.port.in.UpdateCardUseCase;
import com.studydeck.domain.port.in.UpdateDeckUseCase;
import com.studydeck.domain.port.in.UpdateNoteUseCase;
import com.studydeck.domain.port.out.AuditEventPort;
import com.studydeck.domain.port.out.CardRepository;
import com.studydeck.domain.port.out.ClockPort;
import com.studydeck.domain.port.out.DeckRepository;
import com.studydeck.domain.port.out.IdGenerator;
import com.studydeck.domain.port.out.NoteRepository;
import com.studydeck.domain.service.CardGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires application services (use cases) as Spring beans.
 *
 * <p>Pattern: Explicit @Bean (Purist). Domain and application layers stay Spring-free. All wiring
 * happens here in the infrastructure layer.
 */
@Configuration
public class BeanConfiguration {

  // ---------------------------------------------------------------
  // Domain services
  // ---------------------------------------------------------------

  @Bean
  CardGenerator cardGenerator() {
    return new CardGenerator();
  }

  // ---------------------------------------------------------------
  // Deck use cases — all implemented by DeckService; expose each port separately
  // ---------------------------------------------------------------

  @Bean
  DeckService deckService(
      DeckRepository deckRepository,
      AuditEventPort auditEventPort,
      IdGenerator idGenerator,
      ClockPort clockPort) {
    return new DeckService(deckRepository, auditEventPort, idGenerator, clockPort);
  }

  @Bean
  CreateDeckUseCase createDeckUseCase(DeckService deckService) {
    return deckService;
  }

  @Bean
  ListDecksQuery listDecksQuery(DeckService deckService) {
    return deckService;
  }

  @Bean
  GetDeckQuery getDeckQuery(DeckService deckService) {
    return deckService;
  }

  @Bean
  UpdateDeckUseCase updateDeckUseCase(DeckService deckService) {
    return deckService;
  }

  @Bean
  ArchiveDeckUseCase archiveDeckUseCase(DeckService deckService) {
    return deckService;
  }

  @Bean
  DeleteDeckUseCase deleteDeckUseCase(DeckService deckService) {
    return deckService;
  }

  // ---------------------------------------------------------------
  // Note use cases — all implemented by NoteService
  // ---------------------------------------------------------------

  @Bean
  NoteService noteService(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      AuditEventPort auditEventPort,
      IdGenerator idGenerator,
      CardGenerator cardGenerator) {
    return new NoteService(
        deckRepository, noteRepository, cardRepository, auditEventPort, idGenerator, cardGenerator);
  }

  @Bean
  CreateNoteUseCase createNoteUseCase(NoteService noteService) {
    return noteService;
  }

  @Bean
  ListNotesQuery listNotesQuery(NoteService noteService) {
    return noteService;
  }

  @Bean
  GetNoteQuery getNoteQuery(NoteService noteService) {
    return noteService;
  }

  @Bean
  UpdateNoteUseCase updateNoteUseCase(NoteService noteService) {
    return noteService;
  }

  @Bean
  DeleteNoteUseCase deleteNoteUseCase(NoteService noteService) {
    return noteService;
  }

  @Bean
  ListCardsForNoteQuery listCardsForNoteQuery(NoteService noteService) {
    return noteService;
  }

  // ---------------------------------------------------------------
  // Card use cases — all implemented by CardService
  // ---------------------------------------------------------------

  @Bean
  CardService cardService(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      AuditEventPort auditEventPort) {
    return new CardService(deckRepository, noteRepository, cardRepository, auditEventPort);
  }

  @Bean
  GetCardQuery getCardQuery(CardService cardService) {
    return cardService;
  }

  @Bean
  ListCardsQuery listCardsQuery(CardService cardService) {
    return cardService;
  }

  @Bean
  UpdateCardUseCase updateCardUseCase(CardService cardService) {
    return cardService;
  }

  @Bean
  DeleteCardUseCase deleteCardUseCase(CardService cardService) {
    return cardService;
  }
}
