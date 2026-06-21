package com.studydeck.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studydeck.application.exception.NotFoundException;
import com.studydeck.application.support.FixedClockPort;
import com.studydeck.application.support.InMemoryAuditEventPort;
import com.studydeck.application.support.InMemoryCardRepository;
import com.studydeck.application.support.InMemoryCardScheduleStateRepository;
import com.studydeck.application.support.InMemoryDeckRepository;
import com.studydeck.application.support.InMemoryImportJobRepository;
import com.studydeck.application.support.InMemoryNoteHashRepository;
import com.studydeck.application.support.InMemoryNoteRepository;
import com.studydeck.application.support.InMemoryUserAccountRepository;
import com.studydeck.application.support.SequentialIdGenerator;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.UserAccount;
import com.studydeck.domain.port.in.CreateDeckUseCase;
import com.studydeck.domain.port.in.ExportAccountUseCase;
import com.studydeck.domain.service.CardGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Use-case tests for ExportAccountService — plain Java, no Spring. */
class ExportAccountServiceTest {

  private InMemoryUserAccountRepository userRepo;
  private InMemoryDeckRepository deckRepo;
  private InMemoryNoteRepository noteRepo;
  private InMemoryCardRepository cardRepo;
  private InMemoryCardScheduleStateRepository scheduleStateRepo;
  private InMemoryAuditEventPort auditPort;
  private InMemoryImportJobRepository importJobRepo;
  private InMemoryNoteHashRepository noteHashRepo;
  private SequentialIdGenerator idGen;

  private ExportAccountUseCase exportAccount;
  private CreateDeckUseCase createDeck;

  private final OwnerId alice = OwnerId.generate();

  @BeforeEach
  void setUp() {
    userRepo = new InMemoryUserAccountRepository();
    deckRepo = new InMemoryDeckRepository();
    noteRepo = new InMemoryNoteRepository();
    cardRepo = new InMemoryCardRepository();
    scheduleStateRepo = new InMemoryCardScheduleStateRepository();
    auditPort = new InMemoryAuditEventPort();
    importJobRepo = new InMemoryImportJobRepository();
    noteHashRepo = new InMemoryNoteHashRepository();
    idGen = new SequentialIdGenerator();
    FixedClockPort clock = FixedClockPort.epoch();

    CardGenerator cardGenerator = new CardGenerator();

    ImportExportService importExport =
        new ImportExportService(
            deckRepo,
            noteRepo,
            cardRepo,
            scheduleStateRepo,
            clock,
            auditPort,
            idGen,
            cardGenerator,
            importJobRepo,
            noteHashRepo);

    DeckService deckService = new DeckService(deckRepo, auditPort, idGen, clock);
    createDeck = deckService;

    exportAccount = new ExportAccountService(userRepo, deckService, importExport, null);
  }

  @Test
  @DisplayName("throws NotFoundException when the owner does not exist")
  void throwsNotFoundForUnknownOwner() {
    OwnerId unknown = OwnerId.generate();

    assertThatThrownBy(() -> exportAccount.execute(unknown)).isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("returns account data for a known owner with no decks")
  void returnsAccountWithNoDecks() {
    UserAccount account = UserAccount.provision(alice, "alice@example.com", "Alice");
    userRepo.save(account);

    ExportAccountUseCase.Result result = exportAccount.execute(alice);

    assertThat(result.account().getId()).isEqualTo(alice);
    assertThat(result.decks()).isEmpty();
    assertThat(result.documents()).isEmpty();
    assertThat(result.exportedAt()).isNotNull();
  }

  @Test
  @DisplayName("aggregates decks in the export")
  void aggregatesDecks() {
    UserAccount account = UserAccount.provision(alice, "alice@example.com", "Alice");
    userRepo.save(account);

    createDeck.execute(new CreateDeckUseCase.Command(alice, "Deck 1", null));
    createDeck.execute(new CreateDeckUseCase.Command(alice, "Deck 2", null));

    ExportAccountUseCase.Result result = exportAccount.execute(alice);

    assertThat(result.decks()).hasSize(2);
  }
}
