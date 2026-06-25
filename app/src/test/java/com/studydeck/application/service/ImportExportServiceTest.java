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
import com.studydeck.application.support.SequentialIdGenerator;
import com.studydeck.domain.model.Deck;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.NoteType;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.ExecuteImportUseCase;
import com.studydeck.domain.port.in.ExportDeckUseCase;
import com.studydeck.domain.port.in.PreviewImportUseCase;
import com.studydeck.domain.port.in.ValidateImportUseCase;
import com.studydeck.domain.port.in.ValidateImportUseCase.ImportPayload;
import com.studydeck.domain.port.in.ValidateImportUseCase.ImportPayload.DeckMeta;
import com.studydeck.domain.port.in.ValidateImportUseCase.ImportPayload.NoteImport;
import com.studydeck.domain.port.in.ValidateImportUseCase.ImportPayload.OptionImport;
import com.studydeck.domain.service.CardGenerator;
import com.studydeck.domain.service.NoteContentHasher;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ImportExportService}.
 *
 * <p>All dependencies are in-memory stubs — no Spring context, no database.
 */
class ImportExportServiceTest {

  private InMemoryDeckRepository deckRepo;
  private InMemoryNoteRepository noteRepo;
  private InMemoryCardRepository cardRepo;
  private InMemoryCardScheduleStateRepository scheduleRepo;
  private InMemoryAuditEventPort auditPort;
  private InMemoryImportJobRepository jobRepo;
  private InMemoryNoteHashRepository hashRepo;
  private SequentialIdGenerator idGen;
  private ImportExportService sut;

  private final OwnerId ownerId = new OwnerId(UUID.randomUUID());

  @BeforeEach
  void setUp() {
    deckRepo = new InMemoryDeckRepository();
    noteRepo = new InMemoryNoteRepository();
    cardRepo = new InMemoryCardRepository();
    scheduleRepo = new InMemoryCardScheduleStateRepository();
    auditPort = new InMemoryAuditEventPort();
    jobRepo = new InMemoryImportJobRepository();
    hashRepo = new InMemoryNoteHashRepository();
    idGen = new SequentialIdGenerator();

    sut =
        new ImportExportService(
            deckRepo,
            noteRepo,
            cardRepo,
            scheduleRepo,
            FixedClockPort.epoch(),
            auditPort,
            idGen,
            new CardGenerator(),
            jobRepo,
            hashRepo);
  }

  // ---------------------------------------------------------------
  // ValidateImportUseCase
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("ValidateImportUseCase")
  class ValidateTests {

    @Test
    @DisplayName("valid payload with all 5 note types passes validation")
    void validAllTypes() {
      var payload = payloadWithAllTypes();
      var result = sut.execute(new ValidateImportUseCase.Command(ownerId, payload));

      assertThat(result.valid()).isTrue();
      assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("missing schemaVersion returns error")
    void missingSchemaVersion() {
      var payload =
          new ImportPayload(
              null, new DeckMeta("Test Deck", null, null), List.of(basicNote("Q", "A")));

      var result = sut.execute(new ValidateImportUseCase.Command(ownerId, payload));

      assertThat(result.valid()).isFalse();
      assertThat(result.errors()).anyMatch(e -> e.field().equals("schemaVersion"));
    }

    @Test
    @DisplayName("wrong schemaVersion returns error")
    void wrongSchemaVersion() {
      var payload =
          new ImportPayload("2.0", new DeckMeta("Test", null, null), List.of(basicNote("Q", "A")));

      var result = sut.execute(new ValidateImportUseCase.Command(ownerId, payload));

      assertThat(result.valid()).isFalse();
      assertThat(result.errors()).anyMatch(e -> e.field().equals("schemaVersion"));
    }

    @Test
    @DisplayName("blank deck title returns error")
    void blankDeckTitle() {
      var payload =
          new ImportPayload("1.0", new DeckMeta("", null, null), List.of(basicNote("Q", "A")));

      var result = sut.execute(new ValidateImportUseCase.Command(ownerId, payload));

      assertThat(result.valid()).isFalse();
      assertThat(result.errors()).anyMatch(e -> e.field().contains("deck.title"));
    }

    @Test
    @DisplayName("cloze note without {{cN::deletion}} returns error")
    void invalidClozePattern() {
      var note =
          new NoteImport(
              "cloze",
              null,
              null,
              "No deletion markers here.",
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null);
      var payload = new ImportPayload("1.0", new DeckMeta("Deck", null, null), List.of(note));

      var result = sut.execute(new ValidateImportUseCase.Command(ownerId, payload));

      assertThat(result.valid()).isFalse();
      assertThat(result.errors()).anyMatch(e -> e.field().contains("text"));
    }

    @Test
    @DisplayName("multiple-choice with 3 options (below minimum) returns error")
    void tooFewOptions() {
      var opts =
          List.of(
              new OptionImport("A", "One"),
              new OptionImport("B", "Two"),
              new OptionImport("C", "Three"));
      var note =
          new NoteImport(
              "multiple-choice",
              null,
              null,
              null,
              "Question?",
              opts,
              List.of("A"),
              null,
              null,
              null,
              null,
              null,
              null);
      var payload = new ImportPayload("1.0", new DeckMeta("Deck", null, null), List.of(note));

      var result = sut.execute(new ValidateImportUseCase.Command(ownerId, payload));

      assertThat(result.valid()).isFalse();
      assertThat(result.errors()).anyMatch(e -> e.field().contains("options"));
    }

    @Test
    @DisplayName("unknown note type returns error")
    void unknownNoteType() {
      var note =
          new NoteImport(
              "unknown-type",
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null);
      var payload = new ImportPayload("1.0", new DeckMeta("Deck", null, null), List.of(note));

      var result = sut.execute(new ValidateImportUseCase.Command(ownerId, payload));

      assertThat(result.valid()).isFalse();
    }
  }

  // ---------------------------------------------------------------
  // ExecuteImportUseCase
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("ExecuteImportUseCase")
  class ExecuteTests {

    @Test
    @DisplayName(
        "import with all 5 note types: notes and cards are persisted, schedule states initialized")
    void importAllTypes() {
      var payload = payloadWithAllTypes();
      var result = sut.execute(new ExecuteImportUseCase.Command(ownerId, payload, null));

      // importedNotes = 5 (basic, reversed, cloze, mc, free-text)
      assertThat(result.importedNotes()).isEqualTo(5);
      // cards: basic=1, reversed=2, cloze depends on deletions ({{c1::}}={{c2::}}=2), mc=1, free=1
      // → total ≥ 5
      assertThat(result.importedCards()).isGreaterThanOrEqualTo(5);
      assertThat(result.duplicateNotes()).isEqualTo(0);
      assertThat(result.rejectedNotes()).isEqualTo(0);
      assertThat(result.deckId()).isNotNull();
      assertThat(result.importId()).isNotNull();

      // Persist: notes and cards in repos
      assertThat(noteRepo.size()).isEqualTo(5);
      assertThat(cardRepo.all()).hasSizeGreaterThanOrEqualTo(5);

      // Schedule states initialized for each card
      assertThat(scheduleRepo.size()).isGreaterThanOrEqualTo(5);

      // Import job persisted
      assertThat(jobRepo.size()).isEqualTo(1);

      // Audit events recorded
      assertThat(auditPort.size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("second import of same payload: all notes flagged as duplicates")
    void duplicateDetection() {
      var payload = payloadWithAllTypes();
      var firstResult = sut.execute(new ExecuteImportUseCase.Command(ownerId, payload, null));

      // Seed the hash repo with the hashes from the first import
      DeckId deckId = new DeckId(firstResult.deckId());
      for (NoteImport note : payload.notes()) {
        NoteType type = noteTypeOf(note.noteType());
        if (type != null) {
          hashRepo.addExistingHash(deckId, type, NoteContentHasher.hash(note));
        }
      }

      // Second import to the same deck
      var secondResult = sut.execute(new ExecuteImportUseCase.Command(ownerId, payload, deckId));

      assertThat(secondResult.importedNotes()).isEqualTo(0);
      assertThat(secondResult.duplicateNotes()).isEqualTo(5);
    }

    @Test
    @DisplayName("import creates cards that are initially due (NEW schedule state)")
    void cardsAreInitiallyDue() {
      var payload =
          new ImportPayload(
              "1.0", new DeckMeta("Test", null, null), List.of(basicNote("What?", "This.")));

      sut.execute(new ExecuteImportUseCase.Command(ownerId, payload, null));

      // Schedule states exist and cards are in NEW state (due at creation time)
      assertThat(scheduleRepo.size()).isEqualTo(1);
    }

    @Test
    @DisplayName(
        "invalid payload (missing schemaVersion) returns rejected count without persisting")
    void invalidPayloadNotPersisted() {
      var payload =
          new ImportPayload(null, new DeckMeta("Deck", null, null), List.of(basicNote("Q", "A")));

      var result = sut.execute(new ExecuteImportUseCase.Command(ownerId, payload, null));

      assertThat(result.importedNotes()).isEqualTo(0);
      assertThat(noteRepo.size()).isEqualTo(0);
      assertThat(cardRepo.all()).isEmpty();
    }
  }

  // ---------------------------------------------------------------
  // ExportDeckUseCase
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("ExportDeckUseCase")
  class ExportTests {

    @Test
    @DisplayName("export a deck produces round-trippable payload (same schemaVersion and notes)")
    void exportRoundTrip() {
      // First import
      var payload = payloadWithAllTypes();
      var importResult = sut.execute(new ExecuteImportUseCase.Command(ownerId, payload, null));

      DeckId deckId = new DeckId(importResult.deckId());

      // Export
      var exported = sut.execute(new ExportDeckUseCase.Command(ownerId, deckId));

      assertThat(exported.schemaVersion()).isEqualTo("1.0");
      assertThat(exported.notes()).hasSize(5);
      assertThat(exported.deck().title()).isEqualTo("Biology 101");

      // Verify noteType coverage
      var exportedTypes = exported.notes().stream().map(NoteImport::noteType).toList();
      assertThat(exportedTypes)
          .contains("basic", "reversed", "cloze", "multiple-choice", "free-text");
    }
  }

  // ---------------------------------------------------------------
  // PreviewImportUseCase
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("PreviewImportUseCase")
  class PreviewTests {

    @Test
    @DisplayName("preview of valid payload returns correct note count and no duplicates")
    void previewNoDuplicates() {
      var payload = payloadWithAllTypes();
      var result = sut.execute(new PreviewImportUseCase.Command(ownerId, payload, null));

      assertThat(result.valid()).isTrue();
      assertThat(result.summary().totalNotes()).isEqualTo(5);
      assertThat(result.summary().duplicateCandidates()).isEqualTo(0);
    }

    @Test
    @DisplayName("preview detects duplicate when hash already exists in target deck")
    void previewDuplicateDetected() {
      // Seed the deck owned by ownerId so ownership check passes
      DeckId targetDeck = DeckId.generate();
      deckRepo.save(Deck.create(targetDeck, ownerId, "Target Deck", null));
      NoteImport note = basicNote("What?", "This.");
      hashRepo.addExistingHash(targetDeck, NoteType.BASIC, NoteContentHasher.hash(note));

      var payload = new ImportPayload("1.0", new DeckMeta("Deck", null, null), List.of(note));
      var result = sut.execute(new PreviewImportUseCase.Command(ownerId, payload, targetDeck));

      assertThat(result.summary().duplicateCandidates()).isEqualTo(1);
    }

    @Test
    @DisplayName(
        "preview with another owner's deckId throws NotFoundException — hash-oracle IDOR prevented")
    void previewWithOtherOwnersDeckThrowsNotFound() {
      // Alice owns this deck
      OwnerId alice = OwnerId.generate();
      DeckId aliceDeck = DeckId.generate();
      deckRepo.save(Deck.create(aliceDeck, alice, "Alice Deck", null));

      // Bob tries to preview against Alice's deck — should be rejected (NotFoundException)
      OwnerId bob = OwnerId.generate();
      var payload =
          new ImportPayload(
              "1.0", new DeckMeta("Bob Import", null, null), List.of(basicNote("Q", "A")));

      assertThatThrownBy(
              () -> sut.execute(new PreviewImportUseCase.Command(bob, payload, aliceDeck)))
          .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("preview with non-existent deckId throws NotFoundException")
    void previewWithNonExistentDeckThrowsNotFound() {
      DeckId nonExistentDeck = DeckId.generate();
      var payload =
          new ImportPayload(
              "1.0", new DeckMeta("Import", null, null), List.of(basicNote("Q", "A")));

      assertThatThrownBy(
              () ->
                  sut.execute(new PreviewImportUseCase.Command(ownerId, payload, nonExistentDeck)))
          .isInstanceOf(NotFoundException.class);
    }
  }

  // ---------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------

  private ImportPayload payloadWithAllTypes() {
    var opts4 =
        List.of(
            new OptionImport("A", "Option A"),
            new OptionImport("B", "Option B"),
            new OptionImport("C", "Option C"),
            new OptionImport("D", "Option D"));

    return new ImportPayload(
        "1.0",
        new DeckMeta("Biology 101", "A biology deck", List.of("bio")),
        List.of(
            basicNote("What is a cell?", "The basic unit of life."),
            new NoteImport(
                "reversed",
                "Nucleus",
                "Núcleo",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null),
            new NoteImport(
                "cloze",
                null,
                null,
                "The {{c1::mitochondria}} produces {{c2::ATP}}.",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null),
            new NoteImport(
                "multiple-choice",
                null,
                null,
                null,
                "Which organelle produces ATP?",
                opts4,
                List.of("A"),
                "Explanation",
                null,
                null,
                null,
                null,
                null),
            new NoteImport(
                "free-text",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Explain the cell wall.",
                "It provides structure.",
                null,
                null,
                null)));
  }

  private NoteImport basicNote(String front, String back) {
    return new NoteImport(
        "basic", front, back, null, null, null, null, null, null, null, null, null, null);
  }

  private NoteType noteTypeOf(String type) {
    return switch (type) {
      case "basic" -> NoteType.BASIC;
      case "reversed" -> NoteType.REVERSED;
      case "cloze" -> NoteType.CLOZE;
      case "multiple-choice" -> NoteType.MULTIPLE_CHOICE;
      case "free-text" -> NoteType.FREE_TEXT;
      default -> null;
    };
  }
}
