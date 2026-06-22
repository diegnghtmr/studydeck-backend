package com.studydeck.infrastructure.config;

import com.studydeck.application.service.AuthService;
import com.studydeck.application.service.CardService;
import com.studydeck.application.service.DeckService;
import com.studydeck.application.service.DeleteAccountService;
import com.studydeck.application.service.ExportAccountService;
import com.studydeck.application.service.ImportExportService;
import com.studydeck.application.service.NoteService;
import com.studydeck.application.service.NoteTypeService;
import com.studydeck.application.service.ProvisionUserService;
import com.studydeck.application.service.ReviewService;
import com.studydeck.application.service.UserPreferencesService;
import com.studydeck.application.service.UserStatsService;
import com.studydeck.domain.port.in.ArchiveDeckUseCase;
import com.studydeck.domain.port.in.CreateDeckUseCase;
import com.studydeck.domain.port.in.CreateNoteUseCase;
import com.studydeck.domain.port.in.DeleteAccountUseCase;
import com.studydeck.domain.port.in.DeleteCardUseCase;
import com.studydeck.domain.port.in.DeleteDeckUseCase;
import com.studydeck.domain.port.in.DeleteNoteUseCase;
import com.studydeck.domain.port.in.ExecuteImportUseCase;
import com.studydeck.domain.port.in.ExportAccountUseCase;
import com.studydeck.domain.port.in.ExportDeckUseCase;
import com.studydeck.domain.port.in.GetCardQuery;
import com.studydeck.domain.port.in.GetCurrentPrincipalQuery;
import com.studydeck.domain.port.in.GetDeckQuery;
import com.studydeck.domain.port.in.GetDeckStatsQuery;
import com.studydeck.domain.port.in.GetNextCardQuery;
import com.studydeck.domain.port.in.GetNoteQuery;
import com.studydeck.domain.port.in.GetReviewSessionQuery;
import com.studydeck.domain.port.in.GetUserStatsQuery;
import com.studydeck.domain.port.in.ListCardsForNoteQuery;
import com.studydeck.domain.port.in.ListCardsQuery;
import com.studydeck.domain.port.in.ListDecksQuery;
import com.studydeck.domain.port.in.ListDocumentsQuery;
import com.studydeck.domain.port.in.ListDueCardsQuery;
import com.studydeck.domain.port.in.ListNoteTypesQuery;
import com.studydeck.domain.port.in.ListNotesQuery;
import com.studydeck.domain.port.in.ListReviewHistoryQuery;
import com.studydeck.domain.port.in.PreviewImportUseCase;
import com.studydeck.domain.port.in.ProvisionUserUseCase;
import com.studydeck.domain.port.in.StartReviewSessionUseCase;
import com.studydeck.domain.port.in.SubmitReviewUseCase;
import com.studydeck.domain.port.in.UpdateCardUseCase;
import com.studydeck.domain.port.in.UpdateDeckUseCase;
import com.studydeck.domain.port.in.UpdateNoteUseCase;
import com.studydeck.domain.port.in.UpdateUserPreferencesUseCase;
import com.studydeck.domain.port.in.ValidateImportUseCase;
import com.studydeck.domain.port.out.AuditEventPort;
import com.studydeck.domain.port.out.CardRepository;
import com.studydeck.domain.port.out.CardScheduleStateRepository;
import com.studydeck.domain.port.out.ClockPort;
import com.studydeck.domain.port.out.DeckRepository;
import com.studydeck.domain.port.out.IdGenerator;
import com.studydeck.domain.port.out.ImportJobRepository;
import com.studydeck.domain.port.out.NoteHashRepository;
import com.studydeck.domain.port.out.NoteRepository;
import com.studydeck.domain.port.out.ReviewLogRepository;
import com.studydeck.domain.port.out.ReviewSessionRepository;
import com.studydeck.domain.port.out.UserAccountRepository;
import com.studydeck.domain.service.CardGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires application services (use cases) as Spring beans.
 *
 * <p>Pattern: Explicit @Bean (Purist). Domain and application layers stay Spring-free. All wiring
 * happens here in the infrastructure layer.
 *
 * <p>Convention: The concrete service class is NEVER exposed as a Spring bean. Only the individual
 * input port interfaces are registered. This prevents Spring from seeing ambiguous candidates when
 * a controller injects an input port interface.
 */
@Configuration
public class BeanConfiguration {

  // ---------------------------------------------------------------
  // Auth use cases
  // ---------------------------------------------------------------

  @Bean
  GetCurrentPrincipalQuery getCurrentPrincipalQuery() {
    return new AuthService();
  }

  @Bean
  ProvisionUserUseCase provisionUserUseCase(
      UserAccountRepository userAccountRepository, AuditEventPort auditEventPort) {
    return new ProvisionUserService(userAccountRepository, auditEventPort);
  }

  @Bean
  UpdateUserPreferencesUseCase updateUserPreferencesUseCase(
      UserAccountRepository userAccountRepository, AuditEventPort auditEventPort) {
    return new UserPreferencesService(userAccountRepository, auditEventPort);
  }

  // ---------------------------------------------------------------
  // NoteType use cases
  // ---------------------------------------------------------------

  @Bean
  ListNoteTypesQuery listNoteTypesQuery() {
    return new NoteTypeService();
  }

  // ---------------------------------------------------------------
  // Domain services
  // ---------------------------------------------------------------

  @Bean
  CardGenerator cardGenerator() {
    return new CardGenerator();
  }

  // ---------------------------------------------------------------
  // Deck use cases — each port is a separate @Bean returning the interface type.
  // The DeckService instance is shared via a @Bean that returns the concrete type
  // — but we isolate it with package-scope to avoid Spring seeing it as a candidate
  // for interface injection.
  // ---------------------------------------------------------------

  @Bean
  CreateDeckUseCase createDeckUseCase(
      DeckRepository deckRepository,
      AuditEventPort auditEventPort,
      IdGenerator idGenerator,
      ClockPort clockPort) {
    return new DeckService(deckRepository, auditEventPort, idGenerator, clockPort);
  }

  @Bean
  ListDecksQuery listDecksQuery(
      DeckRepository deckRepository,
      AuditEventPort auditEventPort,
      IdGenerator idGenerator,
      ClockPort clockPort) {
    return new DeckService(deckRepository, auditEventPort, idGenerator, clockPort);
  }

  @Bean
  GetDeckQuery getDeckQuery(
      DeckRepository deckRepository,
      AuditEventPort auditEventPort,
      IdGenerator idGenerator,
      ClockPort clockPort) {
    return new DeckService(deckRepository, auditEventPort, idGenerator, clockPort);
  }

  @Bean
  UpdateDeckUseCase updateDeckUseCase(
      DeckRepository deckRepository,
      AuditEventPort auditEventPort,
      IdGenerator idGenerator,
      ClockPort clockPort) {
    return new DeckService(deckRepository, auditEventPort, idGenerator, clockPort);
  }

  @Bean
  ArchiveDeckUseCase archiveDeckUseCase(
      DeckRepository deckRepository,
      AuditEventPort auditEventPort,
      IdGenerator idGenerator,
      ClockPort clockPort) {
    return new DeckService(deckRepository, auditEventPort, idGenerator, clockPort);
  }

  @Bean
  DeleteDeckUseCase deleteDeckUseCase(
      DeckRepository deckRepository,
      AuditEventPort auditEventPort,
      IdGenerator idGenerator,
      ClockPort clockPort) {
    return new DeckService(deckRepository, auditEventPort, idGenerator, clockPort);
  }

  // ---------------------------------------------------------------
  // Note use cases — each port is a separate @Bean returning the interface type.
  // ---------------------------------------------------------------

  @Bean
  CreateNoteUseCase createNoteUseCase(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      CardScheduleStateRepository cardScheduleStateRepository,
      ClockPort clockPort,
      AuditEventPort auditEventPort,
      IdGenerator idGenerator,
      CardGenerator cardGenerator) {
    return new NoteService(
        deckRepository,
        noteRepository,
        cardRepository,
        cardScheduleStateRepository,
        clockPort,
        auditEventPort,
        idGenerator,
        cardGenerator);
  }

  @Bean
  ListNotesQuery listNotesQuery(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      CardScheduleStateRepository cardScheduleStateRepository,
      ClockPort clockPort,
      AuditEventPort auditEventPort,
      IdGenerator idGenerator,
      CardGenerator cardGenerator) {
    return new NoteService(
        deckRepository,
        noteRepository,
        cardRepository,
        cardScheduleStateRepository,
        clockPort,
        auditEventPort,
        idGenerator,
        cardGenerator);
  }

  @Bean
  GetNoteQuery getNoteQuery(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      CardScheduleStateRepository cardScheduleStateRepository,
      ClockPort clockPort,
      AuditEventPort auditEventPort,
      IdGenerator idGenerator,
      CardGenerator cardGenerator) {
    return new NoteService(
        deckRepository,
        noteRepository,
        cardRepository,
        cardScheduleStateRepository,
        clockPort,
        auditEventPort,
        idGenerator,
        cardGenerator);
  }

  @Bean
  UpdateNoteUseCase updateNoteUseCase(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      CardScheduleStateRepository cardScheduleStateRepository,
      ClockPort clockPort,
      AuditEventPort auditEventPort,
      IdGenerator idGenerator,
      CardGenerator cardGenerator) {
    return new NoteService(
        deckRepository,
        noteRepository,
        cardRepository,
        cardScheduleStateRepository,
        clockPort,
        auditEventPort,
        idGenerator,
        cardGenerator);
  }

  @Bean
  DeleteNoteUseCase deleteNoteUseCase(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      CardScheduleStateRepository cardScheduleStateRepository,
      ClockPort clockPort,
      AuditEventPort auditEventPort,
      IdGenerator idGenerator,
      CardGenerator cardGenerator) {
    return new NoteService(
        deckRepository,
        noteRepository,
        cardRepository,
        cardScheduleStateRepository,
        clockPort,
        auditEventPort,
        idGenerator,
        cardGenerator);
  }

  @Bean
  ListCardsForNoteQuery listCardsForNoteQuery(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      CardScheduleStateRepository cardScheduleStateRepository,
      ClockPort clockPort,
      AuditEventPort auditEventPort,
      IdGenerator idGenerator,
      CardGenerator cardGenerator) {
    return new NoteService(
        deckRepository,
        noteRepository,
        cardRepository,
        cardScheduleStateRepository,
        clockPort,
        auditEventPort,
        idGenerator,
        cardGenerator);
  }

  // ---------------------------------------------------------------
  // Card use cases — each port is a separate @Bean returning the interface type.
  // ---------------------------------------------------------------

  @Bean
  GetCardQuery getCardQuery(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      AuditEventPort auditEventPort) {
    return new CardService(deckRepository, noteRepository, cardRepository, auditEventPort);
  }

  @Bean
  ListCardsQuery listCardsQuery(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      AuditEventPort auditEventPort) {
    return new CardService(deckRepository, noteRepository, cardRepository, auditEventPort);
  }

  @Bean
  UpdateCardUseCase updateCardUseCase(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      AuditEventPort auditEventPort) {
    return new CardService(deckRepository, noteRepository, cardRepository, auditEventPort);
  }

  @Bean
  DeleteCardUseCase deleteCardUseCase(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      AuditEventPort auditEventPort) {
    return new CardService(deckRepository, noteRepository, cardRepository, auditEventPort);
  }

  // ---------------------------------------------------------------
  // Review use cases — StartReviewSession, GetReviewSession, GetNextCard,
  // SubmitReview, ListDueCards, ListReviewHistory, GetDeckStats.
  // All implemented by ReviewService; each port is a separate @Bean.
  // ---------------------------------------------------------------

  private ReviewService reviewService(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      CardScheduleStateRepository cardScheduleStateRepository,
      ReviewLogRepository reviewLogRepository,
      ReviewSessionRepository reviewSessionRepository,
      AuditEventPort auditEventPort,
      ClockPort clockPort) {
    return new ReviewService(
        deckRepository,
        noteRepository,
        cardRepository,
        cardScheduleStateRepository,
        reviewLogRepository,
        reviewSessionRepository,
        auditEventPort,
        clockPort);
  }

  @Bean
  StartReviewSessionUseCase startReviewSessionUseCase(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      CardScheduleStateRepository cardScheduleStateRepository,
      ReviewLogRepository reviewLogRepository,
      ReviewSessionRepository reviewSessionRepository,
      AuditEventPort auditEventPort,
      ClockPort clockPort) {
    return reviewService(
        deckRepository,
        noteRepository,
        cardRepository,
        cardScheduleStateRepository,
        reviewLogRepository,
        reviewSessionRepository,
        auditEventPort,
        clockPort);
  }

  @Bean
  GetReviewSessionQuery getReviewSessionQuery(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      CardScheduleStateRepository cardScheduleStateRepository,
      ReviewLogRepository reviewLogRepository,
      ReviewSessionRepository reviewSessionRepository,
      AuditEventPort auditEventPort,
      ClockPort clockPort) {
    return reviewService(
        deckRepository,
        noteRepository,
        cardRepository,
        cardScheduleStateRepository,
        reviewLogRepository,
        reviewSessionRepository,
        auditEventPort,
        clockPort);
  }

  @Bean
  GetNextCardQuery getNextCardQuery(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      CardScheduleStateRepository cardScheduleStateRepository,
      ReviewLogRepository reviewLogRepository,
      ReviewSessionRepository reviewSessionRepository,
      AuditEventPort auditEventPort,
      ClockPort clockPort) {
    return reviewService(
        deckRepository,
        noteRepository,
        cardRepository,
        cardScheduleStateRepository,
        reviewLogRepository,
        reviewSessionRepository,
        auditEventPort,
        clockPort);
  }

  @Bean
  SubmitReviewUseCase submitReviewUseCase(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      CardScheduleStateRepository cardScheduleStateRepository,
      ReviewLogRepository reviewLogRepository,
      ReviewSessionRepository reviewSessionRepository,
      AuditEventPort auditEventPort,
      ClockPort clockPort) {
    return reviewService(
        deckRepository,
        noteRepository,
        cardRepository,
        cardScheduleStateRepository,
        reviewLogRepository,
        reviewSessionRepository,
        auditEventPort,
        clockPort);
  }

  @Bean
  ListDueCardsQuery listDueCardsQuery(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      CardScheduleStateRepository cardScheduleStateRepository,
      ReviewLogRepository reviewLogRepository,
      ReviewSessionRepository reviewSessionRepository,
      AuditEventPort auditEventPort,
      ClockPort clockPort) {
    return reviewService(
        deckRepository,
        noteRepository,
        cardRepository,
        cardScheduleStateRepository,
        reviewLogRepository,
        reviewSessionRepository,
        auditEventPort,
        clockPort);
  }

  @Bean
  ListReviewHistoryQuery listReviewHistoryQuery(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      CardScheduleStateRepository cardScheduleStateRepository,
      ReviewLogRepository reviewLogRepository,
      ReviewSessionRepository reviewSessionRepository,
      AuditEventPort auditEventPort,
      ClockPort clockPort) {
    return reviewService(
        deckRepository,
        noteRepository,
        cardRepository,
        cardScheduleStateRepository,
        reviewLogRepository,
        reviewSessionRepository,
        auditEventPort,
        clockPort);
  }

  @Bean
  GetDeckStatsQuery getDeckStatsQuery(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      CardScheduleStateRepository cardScheduleStateRepository,
      ReviewLogRepository reviewLogRepository,
      ReviewSessionRepository reviewSessionRepository,
      AuditEventPort auditEventPort,
      ClockPort clockPort) {
    return reviewService(
        deckRepository,
        noteRepository,
        cardRepository,
        cardScheduleStateRepository,
        reviewLogRepository,
        reviewSessionRepository,
        auditEventPort,
        clockPort);
  }

  // ---------------------------------------------------------------
  // User stats use case — GetUserStatsQuery implemented by UserStatsService.
  // ---------------------------------------------------------------

  @Bean
  GetUserStatsQuery getUserStatsQuery(
      CardScheduleStateRepository cardScheduleStateRepository,
      ReviewLogRepository reviewLogRepository,
      UserAccountRepository userAccountRepository,
      ClockPort clockPort) {
    return new UserStatsService(
        cardScheduleStateRepository, reviewLogRepository, userAccountRepository, clockPort);
  }

  // ---------------------------------------------------------------
  // Import/Export use cases — all implemented by ImportExportService.
  // Each port is a separate @Bean to avoid Spring ambiguity.
  // ---------------------------------------------------------------

  private ImportExportService importExportService(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      CardScheduleStateRepository cardScheduleStateRepository,
      ClockPort clockPort,
      AuditEventPort auditEventPort,
      IdGenerator idGenerator,
      CardGenerator cardGenerator,
      ImportJobRepository importJobRepository,
      NoteHashRepository noteHashRepository) {
    return new ImportExportService(
        deckRepository,
        noteRepository,
        cardRepository,
        cardScheduleStateRepository,
        clockPort,
        auditEventPort,
        idGenerator,
        cardGenerator,
        importJobRepository,
        noteHashRepository);
  }

  @Bean
  ValidateImportUseCase validateImportUseCase(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      CardScheduleStateRepository cardScheduleStateRepository,
      ClockPort clockPort,
      AuditEventPort auditEventPort,
      IdGenerator idGenerator,
      CardGenerator cardGenerator,
      ImportJobRepository importJobRepository,
      NoteHashRepository noteHashRepository) {
    return importExportService(
        deckRepository,
        noteRepository,
        cardRepository,
        cardScheduleStateRepository,
        clockPort,
        auditEventPort,
        idGenerator,
        cardGenerator,
        importJobRepository,
        noteHashRepository);
  }

  @Bean
  PreviewImportUseCase previewImportUseCase(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      CardScheduleStateRepository cardScheduleStateRepository,
      ClockPort clockPort,
      AuditEventPort auditEventPort,
      IdGenerator idGenerator,
      CardGenerator cardGenerator,
      ImportJobRepository importJobRepository,
      NoteHashRepository noteHashRepository) {
    return importExportService(
        deckRepository,
        noteRepository,
        cardRepository,
        cardScheduleStateRepository,
        clockPort,
        auditEventPort,
        idGenerator,
        cardGenerator,
        importJobRepository,
        noteHashRepository);
  }

  @Bean
  ExecuteImportUseCase executeImportUseCase(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      CardScheduleStateRepository cardScheduleStateRepository,
      ClockPort clockPort,
      AuditEventPort auditEventPort,
      IdGenerator idGenerator,
      CardGenerator cardGenerator,
      ImportJobRepository importJobRepository,
      NoteHashRepository noteHashRepository) {
    return importExportService(
        deckRepository,
        noteRepository,
        cardRepository,
        cardScheduleStateRepository,
        clockPort,
        auditEventPort,
        idGenerator,
        cardGenerator,
        importJobRepository,
        noteHashRepository);
  }

  @Bean
  ExportDeckUseCase exportDeckUseCase(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      CardScheduleStateRepository cardScheduleStateRepository,
      ClockPort clockPort,
      AuditEventPort auditEventPort,
      IdGenerator idGenerator,
      CardGenerator cardGenerator,
      ImportJobRepository importJobRepository,
      NoteHashRepository noteHashRepository) {
    return importExportService(
        deckRepository,
        noteRepository,
        cardRepository,
        cardScheduleStateRepository,
        clockPort,
        auditEventPort,
        idGenerator,
        cardGenerator,
        importJobRepository,
        noteHashRepository);
  }

  // ---------------------------------------------------------------
  // GDPR account use cases
  // ---------------------------------------------------------------

  @Bean
  ExportAccountUseCase exportAccountUseCase(
      UserAccountRepository userAccountRepository,
      @org.springframework.beans.factory.annotation.Qualifier("listDecksQuery")
          ListDecksQuery listDecksQuery,
      @org.springframework.beans.factory.annotation.Qualifier("exportDeckUseCase")
          ExportDeckUseCase exportDeckUseCase,
      @org.springframework.beans.factory.annotation.Qualifier("listDocumentsQuery")
          ListDocumentsQuery listDocumentsQuery) {
    return new ExportAccountService(
        userAccountRepository, listDecksQuery, exportDeckUseCase, listDocumentsQuery);
  }

  @Bean
  DeleteAccountUseCase deleteAccountUseCase(
      UserAccountRepository userAccountRepository, AuditEventPort auditEventPort) {
    return new DeleteAccountService(userAccountRepository, auditEventPort);
  }
}
