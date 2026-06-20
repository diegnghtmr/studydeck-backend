package com.studydeck.application.service;

import com.studydeck.application.common.Page;
import com.studydeck.application.exception.NotFoundException;
import com.studydeck.domain.model.Card;
import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.CardScheduleState;
import com.studydeck.domain.model.Deck;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.Note;
import com.studydeck.domain.model.NoteId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.CreateNoteUseCase;
import com.studydeck.domain.port.in.DeleteNoteUseCase;
import com.studydeck.domain.port.in.GetNoteQuery;
import com.studydeck.domain.port.in.ListCardsForNoteQuery;
import com.studydeck.domain.port.in.ListNotesQuery;
import com.studydeck.domain.port.in.UpdateNoteUseCase;
import com.studydeck.domain.port.out.AuditEventPort;
import com.studydeck.domain.port.out.CardRepository;
import com.studydeck.domain.port.out.CardScheduleStateRepository;
import com.studydeck.domain.port.out.ClockPort;
import com.studydeck.domain.port.out.DeckRepository;
import com.studydeck.domain.port.out.IdGenerator;
import com.studydeck.domain.port.out.NoteRepository;
import com.studydeck.domain.service.CardGenerator;
import java.util.List;

/**
 * Application service implementing all Note use cases.
 *
 * <p>Orchestrates: ownership check → domain creation → CardGenerator → atomic persist → audit.
 *
 * <p>Framework-free: no Spring annotations. Wired as {@code @Bean} in {@code BeanConfiguration}.
 */
public final class NoteService
    implements CreateNoteUseCase,
        ListNotesQuery,
        GetNoteQuery,
        UpdateNoteUseCase,
        DeleteNoteUseCase,
        ListCardsForNoteQuery {

  private final DeckRepository deckRepository;
  private final NoteRepository noteRepository;
  private final CardRepository cardRepository;
  private final CardScheduleStateRepository scheduleStateRepository;
  private final ClockPort clockPort;
  private final AuditEventPort auditPort;
  private final IdGenerator idGenerator;
  private final CardGenerator cardGenerator;

  public NoteService(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      CardScheduleStateRepository scheduleStateRepository,
      ClockPort clockPort,
      AuditEventPort auditPort,
      IdGenerator idGenerator,
      CardGenerator cardGenerator) {
    this.deckRepository = deckRepository;
    this.noteRepository = noteRepository;
    this.cardRepository = cardRepository;
    this.scheduleStateRepository = scheduleStateRepository;
    this.clockPort = clockPort;
    this.auditPort = auditPort;
    this.idGenerator = idGenerator;
    this.cardGenerator = cardGenerator;
  }

  // ---------------------------------------------------------------
  // CreateNoteUseCase
  // ---------------------------------------------------------------

  @Override
  public CreateNoteUseCase.Result execute(CreateNoteUseCase.Command command) {
    // 1. Ownership check
    findOwnedDeck(command.ownerId(), command.deckId());

    // 2. Create Note domain object
    NoteId noteId = new NoteId(idGenerator.generate());
    Note note = Note.create(noteId, command.deckId(), command.content(), command.tags());

    // 3. Generate cards via domain service
    List<Card> cards = cardGenerator.generate(note);

    // 4. Atomic persist: note + cards
    noteRepository.save(note);
    cardRepository.saveAll(cards);

    // 5. Initialize schedule state for each new card (NEW, due immediately)
    initScheduleStates(command.ownerId(), cards);

    // 6. Audit
    auditPort.record(command.ownerId(), "note.created", "Note", noteId.toString());

    List<CardId> cardIds = cards.stream().map(Card::getId).toList();
    return new CreateNoteUseCase.Result(noteId, cardIds);
  }

  // ---------------------------------------------------------------
  // ListNotesQuery
  // ---------------------------------------------------------------

  @Override
  public Page<Note> execute(ListNotesQuery.Query query) {
    int offset = query.pageRequest().offset();
    int limit = query.pageRequest().size();
    int page = query.pageRequest().page();

    List<Note> content =
        noteRepository.findAll(
            query.deckId(), query.noteType(), query.tag(), query.search(), offset, limit);
    long total =
        noteRepository.countAll(query.deckId(), query.noteType(), query.tag(), query.search());
    return Page.of(content, page, limit, total);
  }

  // ---------------------------------------------------------------
  // GetNoteQuery
  // ---------------------------------------------------------------

  @Override
  public Note execute(GetNoteQuery.Query query) {
    return findOwnedNote(query.ownerId(), query.noteId());
  }

  // ---------------------------------------------------------------
  // UpdateNoteUseCase
  // ---------------------------------------------------------------

  @Override
  public UpdateNoteUseCase.Result execute(UpdateNoteUseCase.Command command) {
    Note note = findOwnedNote(command.ownerId(), command.noteId());

    // Update domain
    note.updateContent(command.content());
    if (command.tags() != null) {
      note.updateTags(command.tags());
    }

    // Re-generate cards: delete old, create new
    cardRepository.deleteByNoteId(note.getId());
    List<Card> newCards = cardGenerator.generate(note);
    cardRepository.saveAll(newCards);

    // Initialize schedule state for regenerated cards
    initScheduleStates(command.ownerId(), newCards);

    // Persist updated note
    noteRepository.save(note);

    auditPort.record(command.ownerId(), "note.updated", "Note", note.getId().toString());

    List<CardId> cardIds = newCards.stream().map(Card::getId).toList();
    return new UpdateNoteUseCase.Result(note.getId(), cardIds, note.getVersion());
  }

  // ---------------------------------------------------------------
  // DeleteNoteUseCase
  // ---------------------------------------------------------------

  @Override
  public void execute(DeleteNoteUseCase.Command command) {
    Note note = findOwnedNote(command.ownerId(), command.noteId());

    cardRepository.deleteByNoteId(note.getId());
    noteRepository.deleteById(note.getId());

    auditPort.record(command.ownerId(), "note.deleted", "Note", note.getId().toString());
  }

  // ---------------------------------------------------------------
  // ListCardsForNoteQuery
  // ---------------------------------------------------------------

  @Override
  public List<Card> execute(ListCardsForNoteQuery.Query query) {
    // Ownership check via note lookup
    findOwnedNote(query.ownerId(), query.noteId());
    return cardRepository.findByNoteId(query.noteId());
  }

  // ---------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------

  private void initScheduleStates(OwnerId ownerId, List<Card> cards) {
    java.time.Instant now = clockPort.now();
    for (Card card : cards) {
      scheduleStateRepository.save(ownerId, card.getId(), CardScheduleState.newFsrsCard(now));
    }
  }

  private Deck findOwnedDeck(OwnerId ownerId, DeckId deckId) {
    Deck deck =
        deckRepository
            .findById(deckId)
            .orElseThrow(() -> new NotFoundException("Deck", deckId.toString()));
    if (!deck.getOwnerId().equals(ownerId)) {
      throw new NotFoundException("Deck", deckId.toString());
    }
    return deck;
  }

  private Note findOwnedNote(OwnerId ownerId, NoteId noteId) {
    Note note =
        noteRepository
            .findById(noteId)
            .orElseThrow(() -> new NotFoundException("Note", noteId.toString()));
    // Verify ownership via the deck
    Deck deck =
        deckRepository
            .findById(note.getDeckId())
            .orElseThrow(() -> new NotFoundException("Note", noteId.toString()));
    if (!deck.getOwnerId().equals(ownerId)) {
      throw new NotFoundException("Note", noteId.toString());
    }
    return note;
  }
}
