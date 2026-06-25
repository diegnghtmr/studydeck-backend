package com.studydeck.application.service;

import com.studydeck.application.exception.NotFoundException;
import com.studydeck.domain.model.Card;
import com.studydeck.domain.model.CardScheduleState;
import com.studydeck.domain.model.Deck;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.Note;
import com.studydeck.domain.model.NoteContent;
import com.studydeck.domain.model.NoteId;
import com.studydeck.domain.model.NoteType;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.ExecuteImportUseCase;
import com.studydeck.domain.port.in.ExportDeckUseCase;
import com.studydeck.domain.port.in.PreviewImportUseCase;
import com.studydeck.domain.port.in.ValidateImportUseCase;
import com.studydeck.domain.port.in.ValidateImportUseCase.ImportPayload;
import com.studydeck.domain.port.in.ValidateImportUseCase.ImportPayload.NoteImport;
import com.studydeck.domain.port.in.ValidateImportUseCase.ImportPayload.OptionImport;
import com.studydeck.domain.port.out.AuditEventPort;
import com.studydeck.domain.port.out.CardRepository;
import com.studydeck.domain.port.out.CardScheduleStateRepository;
import com.studydeck.domain.port.out.ClockPort;
import com.studydeck.domain.port.out.DeckRepository;
import com.studydeck.domain.port.out.IdGenerator;
import com.studydeck.domain.port.out.ImportJobRepository;
import com.studydeck.domain.port.out.NoteHashRepository;
import com.studydeck.domain.port.out.NoteRepository;
import com.studydeck.domain.service.CardGenerator;
import com.studydeck.domain.service.NoteContentHasher;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Application service implementing all import/export use cases.
 *
 * <p>Import path reuses the SAME domain objects and port calls as {@link NoteService#execute} so
 * that card generation, schedule-state initialisation, and audit all follow the identical path.
 *
 * <p>Framework-free: no Spring annotations. Wired as {@code @Bean} in {@code BeanConfiguration}.
 */
public final class ImportExportService
    implements ValidateImportUseCase,
        PreviewImportUseCase,
        ExecuteImportUseCase,
        ExportDeckUseCase {

  private final DeckRepository deckRepository;
  private final NoteRepository noteRepository;
  private final CardRepository cardRepository;
  private final CardScheduleStateRepository scheduleStateRepository;
  private final ClockPort clockPort;
  private final AuditEventPort auditPort;
  private final IdGenerator idGenerator;
  private final CardGenerator cardGenerator;
  private final ImportJobRepository importJobRepository;
  private final NoteHashRepository noteHashRepository;

  public ImportExportService(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      CardScheduleStateRepository scheduleStateRepository,
      ClockPort clockPort,
      AuditEventPort auditPort,
      IdGenerator idGenerator,
      CardGenerator cardGenerator,
      ImportJobRepository importJobRepository,
      NoteHashRepository noteHashRepository) {
    this.deckRepository = deckRepository;
    this.noteRepository = noteRepository;
    this.cardRepository = cardRepository;
    this.scheduleStateRepository = scheduleStateRepository;
    this.clockPort = clockPort;
    this.auditPort = auditPort;
    this.idGenerator = idGenerator;
    this.cardGenerator = cardGenerator;
    this.importJobRepository = importJobRepository;
    this.noteHashRepository = noteHashRepository;
  }

  // ---------------------------------------------------------------
  // ValidateImportUseCase
  // ---------------------------------------------------------------

  @Override
  public ValidateImportUseCase.Result execute(ValidateImportUseCase.Command command) {
    ImportPayload payload = command.payload();
    List<ValidateImportUseCase.Violation> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    validatePayload(payload, errors, warnings);

    if (errors.isEmpty()) {
      return ValidateImportUseCase.Result.valid(warnings);
    }
    return ValidateImportUseCase.Result.invalid(errors, warnings);
  }

  // ---------------------------------------------------------------
  // PreviewImportUseCase
  // ---------------------------------------------------------------

  @Override
  public PreviewImportUseCase.Result execute(PreviewImportUseCase.Command command) {
    ImportPayload payload = command.payload();
    List<ValidateImportUseCase.Violation> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    validatePayload(payload, errors, warnings);

    if (!errors.isEmpty()) {
      return new PreviewImportUseCase.Result(
          false,
          new PreviewImportUseCase.Result.Summary(payload.deck().title(), 0, 0, 0),
          payload,
          warnings);
    }

    // Dedup detection: check existing hashes per note type
    int duplicateCandidates = 0;
    int predictedCards = 0;

    if (command.targetDeckId() != null) {
      // Ownership check — same guard as ExecuteImport to prevent hash-oracle IDOR.
      Deck targetDeck =
          deckRepository
              .findById(command.targetDeckId())
              .orElseThrow(() -> new NotFoundException("Deck", command.targetDeckId().toString()));
      if (!targetDeck.getOwnerId().equals(command.ownerId())) {
        throw new NotFoundException("Deck", command.targetDeckId().toString());
      }

      for (NoteImport noteImport : payload.notes()) {
        NoteType noteType = noteTypeOf(noteImport.noteType());
        if (noteType == null) continue;

        Set<String> existingHashes =
            noteHashRepository.findExistingHashes(command.targetDeckId(), noteType);
        String hash = NoteContentHasher.hash(noteImport);
        if (existingHashes.contains(hash)) {
          duplicateCandidates++;
          warnings.add(
              "Note at index "
                  + payload.notes().indexOf(noteImport)
                  + " is a duplicate (same content already in deck).");
          continue;
        }
        predictedCards += estimateCards(noteImport);
      }
    } else {
      for (NoteImport noteImport : payload.notes()) {
        predictedCards += estimateCards(noteImport);
      }
    }

    var summary =
        new PreviewImportUseCase.Result.Summary(
            payload.deck().title(), payload.notes().size(), predictedCards, duplicateCandidates);

    return new PreviewImportUseCase.Result(true, summary, payload, warnings);
  }

  // ---------------------------------------------------------------
  // ExecuteImportUseCase
  // ---------------------------------------------------------------

  @Override
  public ExecuteImportUseCase.ImportResult execute(ExecuteImportUseCase.Command command) {
    OwnerId ownerId = command.ownerId();
    ImportPayload payload = command.payload();

    // 1. Validate
    List<ValidateImportUseCase.Violation> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    validatePayload(payload, errors, warnings);
    if (!errors.isEmpty()) {
      // All notes rejected due to schema/domain errors
      errors.forEach(e -> warnings.add("[" + e.field() + "] " + e.message()));
      UUID jobId =
          importJobRepository.save(
              ownerId,
              command.targetDeckId(),
              payload.schemaVersion(),
              0,
              0,
              0,
              payload.notes().size(),
              warnings);
      return new ExecuteImportUseCase.ImportResult(
          jobId,
          command.targetDeckId() != null ? command.targetDeckId().value() : null,
          0,
          0,
          0,
          payload.notes().size(),
          warnings);
    }

    // 2. Resolve or create target deck
    final DeckId deckId;
    if (command.targetDeckId() == null) {
      // Create a new deck from payload metadata
      deckId = createDeckFromPayload(ownerId, payload);
    } else {
      DeckId target = command.targetDeckId();
      // Verify ownership
      Deck deck =
          deckRepository
              .findById(target)
              .orElseThrow(() -> new NotFoundException("Deck", target.toString()));
      if (!deck.getOwnerId().equals(ownerId)) {
        throw new NotFoundException("Deck", target.toString());
      }
      deckId = target;
    }

    // 3. Process notes
    int importedNotes = 0;
    int importedCards = 0;
    int duplicateNotes = 0;
    int rejectedNotes = 0;
    int noteIndex = 0;

    for (NoteImport noteImport : payload.notes()) {
      try {
        NoteType noteType = noteTypeOf(noteImport.noteType());
        if (noteType == null) {
          rejectedNotes++;
          warnings.add("Note[" + noteIndex + "]: unknown noteType '" + noteImport.noteType() + "'");
          noteIndex++;
          continue;
        }

        // Dedup check
        Set<String> existingHashes = noteHashRepository.findExistingHashes(deckId, noteType);
        String hash = NoteContentHasher.hash(noteImport);
        if (existingHashes.contains(hash)) {
          duplicateNotes++;
          noteIndex++;
          continue;
        }

        // Build domain NoteContent — let domain validate
        NoteContent content = toDomainContent(noteImport);

        // Reuse same path as NoteService.execute
        NoteId noteId = new NoteId(idGenerator.generate());
        Note note = Note.create(noteId, deckId, content, noteImport.tags());

        // Generate cards
        List<Card> cards = cardGenerator.generate(note);

        // Atomic persist: note + cards
        noteRepository.save(note);
        cardRepository.saveAll(cards);

        // Persist content hash for future dedup
        noteHashRepository.saveHash(noteId, hash);

        // Initialize schedule state for each card (NEW, due immediately)
        java.time.Instant now = clockPort.now();
        for (Card card : cards) {
          scheduleStateRepository.save(ownerId, card.getId(), CardScheduleState.newFsrsCard(now));
        }

        auditPort.record(ownerId, "note.imported", "Note", noteId.toString());

        importedNotes++;
        importedCards += cards.size();

      } catch (Exception e) {
        rejectedNotes++;
        warnings.add("Note[" + noteIndex + "]: rejected — " + e.getMessage());
      }
      noteIndex++;
    }

    // 4. Persist import job
    UUID jobId =
        importJobRepository.save(
            ownerId,
            deckId,
            payload.schemaVersion(),
            importedNotes,
            importedCards,
            duplicateNotes,
            rejectedNotes,
            warnings);

    auditPort.record(ownerId, "import.executed", "ImportJob", jobId.toString());

    return new ExecuteImportUseCase.ImportResult(
        jobId,
        deckId.value(),
        importedNotes,
        importedCards,
        duplicateNotes,
        rejectedNotes,
        warnings);
  }

  // ---------------------------------------------------------------
  // ExportDeckUseCase
  // ---------------------------------------------------------------

  @Override
  public ImportPayload execute(ExportDeckUseCase.Command command) {
    OwnerId ownerId = command.ownerId();
    DeckId deckId = command.deckId();

    Deck deck =
        deckRepository
            .findById(deckId)
            .orElseThrow(() -> new NotFoundException("Deck", deckId.toString()));
    if (!deck.getOwnerId().equals(ownerId)) {
      throw new NotFoundException("Deck", deckId.toString());
    }

    // Load all notes for this deck (owner-scoped)
    List<Note> notes =
        noteRepository.findAll(ownerId, deckId, null, null, null, 0, Integer.MAX_VALUE);

    List<NoteImport> noteImports = notes.stream().map(this::toNoteImport).toList();

    var deckMeta =
        new ImportPayload.DeckMeta(deck.getTitle(), deck.getDescription(), deck.getTags());

    return new ImportPayload("1.0", deckMeta, noteImports);
  }

  // ---------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------

  private void validatePayload(
      ImportPayload payload, List<ValidateImportUseCase.Violation> errors, List<String> warnings) {
    if (payload == null) {
      errors.add(ValidateImportUseCase.Violation.of("payload", "must not be null"));
      return;
    }
    if (!"1.0".equals(payload.schemaVersion())) {
      errors.add(
          ValidateImportUseCase.Violation.of(
              "schemaVersion", "must be '1.0', got: " + payload.schemaVersion()));
    }
    if (payload.deck() == null || isBlank(payload.deck().title())) {
      errors.add(ValidateImportUseCase.Violation.of("deck.title", "must not be blank"));
    }
    if (payload.notes() == null || payload.notes().isEmpty()) {
      errors.add(ValidateImportUseCase.Violation.of("notes", "must not be empty"));
      return;
    }

    int idx = 0;
    for (NoteImport note : payload.notes()) {
      validateNoteImport(note, idx, errors, warnings);
      idx++;
    }
  }

  private void validateNoteImport(
      NoteImport note,
      int idx,
      List<ValidateImportUseCase.Violation> errors,
      List<String> warnings) {
    if (note == null) {
      errors.add(ValidateImportUseCase.Violation.of("notes[" + idx + "]", "must not be null"));
      return;
    }
    if (isBlank(note.noteType())) {
      errors.add(
          ValidateImportUseCase.Violation.of("notes[" + idx + "].noteType", "must not be blank"));
      return;
    }
    switch (note.noteType()) {
      case "basic", "reversed" -> {
        if (isBlank(note.front())) {
          errors.add(
              ValidateImportUseCase.Violation.of("notes[" + idx + "].front", "must not be blank"));
        }
        if (isBlank(note.back())) {
          errors.add(
              ValidateImportUseCase.Violation.of("notes[" + idx + "].back", "must not be blank"));
        }
      }
      case "cloze" -> {
        if (isBlank(note.text())) {
          errors.add(
              ValidateImportUseCase.Violation.of("notes[" + idx + "].text", "must not be blank"));
        } else if (!note.text().matches(".*\\{\\{c[0-9]+::.+\\}\\}.*")) {
          errors.add(
              ValidateImportUseCase.Violation.of(
                  "notes[" + idx + "].text",
                  "cloze text must contain at least one {{cN::deletion}} marker"));
        }
      }
      case "multiple-choice" -> {
        if (isBlank(note.question())) {
          errors.add(
              ValidateImportUseCase.Violation.of(
                  "notes[" + idx + "].question", "must not be blank"));
        }
        if (note.options() == null || note.options().size() < 4 || note.options().size() > 5) {
          errors.add(
              ValidateImportUseCase.Violation.of(
                  "notes[" + idx + "].options", "must have 4 or 5 options"));
        }
        if (note.correctOptionKeys() == null || note.correctOptionKeys().isEmpty()) {
          errors.add(
              ValidateImportUseCase.Violation.of(
                  "notes[" + idx + "].correctOptionKeys", "must have exactly 1 correct key"));
        }
      }
      case "free-text" -> {
        if (isBlank(note.prompt())) {
          errors.add(
              ValidateImportUseCase.Violation.of("notes[" + idx + "].prompt", "must not be blank"));
        }
        if (isBlank(note.expectedAnswer())) {
          errors.add(
              ValidateImportUseCase.Violation.of(
                  "notes[" + idx + "].expectedAnswer", "must not be blank"));
        }
      }
      default ->
          errors.add(
              ValidateImportUseCase.Violation.of(
                  "notes[" + idx + "].noteType", "unknown noteType '" + note.noteType() + "'"));
    }
  }

  private NoteContent toDomainContent(NoteImport note) {
    return switch (note.noteType()) {
      case "basic" -> new NoteContent.Basic(note.front(), note.back());
      case "reversed" -> new NoteContent.Reversed(note.front(), note.back());
      case "cloze" -> new NoteContent.Cloze(note.text());
      case "multiple-choice" -> {
        List<NoteContent.MultipleChoice.Option> opts =
            note.options().stream()
                .map(o -> new NoteContent.MultipleChoice.Option(o.key(), o.text()))
                .toList();
        yield new NoteContent.MultipleChoice(
            note.question(), opts, note.correctOptionKeys(), note.explanation());
      }
      case "free-text" ->
          new NoteContent.FreeText(note.prompt(), note.expectedAnswer(), note.gradingGuidance());
      default -> throw new IllegalArgumentException("Unknown noteType: " + note.noteType());
    };
  }

  private NoteImport toNoteImport(Note note) {
    return switch (note.getContent()) {
      case NoteContent.Basic b ->
          new NoteImport(
              "basic",
              b.front(),
              b.back(),
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              note.getTags(),
              null);
      case NoteContent.Reversed r ->
          new NoteImport(
              "reversed",
              r.front(),
              r.back(),
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              note.getTags(),
              null);
      case NoteContent.Cloze c ->
          new NoteImport(
              "cloze",
              null,
              null,
              c.text(),
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              note.getTags(),
              null);
      case NoteContent.MultipleChoice mc -> {
        List<OptionImport> opts =
            mc.options().stream().map(o -> new OptionImport(o.key(), o.text())).toList();
        yield new NoteImport(
            "multiple-choice",
            null,
            null,
            null,
            mc.question(),
            opts,
            mc.correctOptionKeys(),
            mc.explanation(),
            null,
            null,
            null,
            note.getTags(),
            null);
      }
      case NoteContent.FreeText ft ->
          new NoteImport(
              "free-text",
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              ft.prompt(),
              ft.expectedAnswer(),
              ft.gradingGuidance(),
              note.getTags(),
              null);
    };
  }

  private DeckId createDeckFromPayload(OwnerId ownerId, ImportPayload payload) {
    DeckId deckId = new DeckId(idGenerator.generate());
    Deck deck =
        Deck.create(
            deckId,
            ownerId,
            payload.deck().title(),
            payload.deck().description(),
            payload.deck().tags() != null ? payload.deck().tags() : List.of(),
            0.9);
    deckRepository.save(deck);
    auditPort.record(ownerId, "deck.created", "Deck", deckId.toString());
    return deckId;
  }

  private int estimateCards(NoteImport note) {
    return switch (note.noteType()) {
      case "reversed" -> 2;
      case "cloze" -> {
        long count =
            note.text() == null
                ? 1
                : note.text().chars().filter(c -> c == '{').count() / 2; // rough estimate
        // Use regex-based count of unique {{cN::...}} groups
        if (note.text() != null) {
          java.util.regex.Matcher m =
              java.util.regex.Pattern.compile("\\{\\{c(\\d+)::.+?\\}\\}").matcher(note.text());
          java.util.TreeSet<Integer> ns = new java.util.TreeSet<>();
          while (m.find()) ns.add(Integer.parseInt(m.group(1)));
          yield Math.max(1, ns.size());
        }
        yield Math.max(1, (int) count);
      }
      default -> 1;
    };
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

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
