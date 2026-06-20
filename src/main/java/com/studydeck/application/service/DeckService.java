package com.studydeck.application.service;

import com.studydeck.application.common.Page;
import com.studydeck.application.exception.NotFoundException;
import com.studydeck.domain.model.Deck;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.ArchiveDeckUseCase;
import com.studydeck.domain.port.in.CreateDeckUseCase;
import com.studydeck.domain.port.in.DeleteDeckUseCase;
import com.studydeck.domain.port.in.GetDeckQuery;
import com.studydeck.domain.port.in.ListDecksQuery;
import com.studydeck.domain.port.in.UpdateDeckUseCase;
import com.studydeck.domain.port.out.AuditEventPort;
import com.studydeck.domain.port.out.ClockPort;
import com.studydeck.domain.port.out.DeckRepository;
import com.studydeck.domain.port.out.IdGenerator;
import java.util.List;

/**
 * Application service implementing all Deck use cases.
 *
 * <p>Framework-free: no Spring annotations. Wired as {@code @Bean} in {@code BeanConfiguration}.
 */
public final class DeckService
    implements CreateDeckUseCase,
        ListDecksQuery,
        GetDeckQuery,
        UpdateDeckUseCase,
        ArchiveDeckUseCase,
        DeleteDeckUseCase {

  private final DeckRepository deckRepository;
  private final AuditEventPort auditPort;
  private final IdGenerator idGenerator;

  @SuppressWarnings("unused")
  private final ClockPort clock;

  public DeckService(
      DeckRepository deckRepository,
      AuditEventPort auditPort,
      IdGenerator idGenerator,
      ClockPort clock) {
    this.deckRepository = deckRepository;
    this.auditPort = auditPort;
    this.idGenerator = idGenerator;
    this.clock = clock;
  }

  // ---------------------------------------------------------------
  // CreateDeckUseCase
  // ---------------------------------------------------------------

  @Override
  public DeckId execute(CreateDeckUseCase.Command command) {
    DeckId id = new DeckId(idGenerator.generate());
    Deck deck =
        Deck.create(
            id,
            command.ownerId(),
            command.title(),
            command.description(),
            command.tags(),
            command.defaultDesiredRetention());
    deckRepository.save(deck);
    auditPort.record(command.ownerId(), "deck.created", "Deck", id.toString());
    return id;
  }

  // ---------------------------------------------------------------
  // ListDecksQuery
  // ---------------------------------------------------------------

  @Override
  public Page<Deck> execute(ListDecksQuery.Query query) {
    OwnerId ownerId = query.ownerId();
    boolean includeArchived = query.includeArchived();
    String search = query.search();
    int offset = query.pageRequest().offset();
    int limit = query.pageRequest().size();
    int page = query.pageRequest().page();

    List<Deck> content =
        deckRepository.findByOwner(ownerId, includeArchived, search, offset, limit);
    long total = deckRepository.countByOwner(ownerId, includeArchived, search);
    return Page.of(content, page, limit, total);
  }

  // ---------------------------------------------------------------
  // GetDeckQuery
  // ---------------------------------------------------------------

  @Override
  public Deck execute(GetDeckQuery.Query query) {
    return findOwnedDeck(query.ownerId(), query.deckId());
  }

  // ---------------------------------------------------------------
  // UpdateDeckUseCase
  // ---------------------------------------------------------------

  @Override
  public void execute(UpdateDeckUseCase.Command command) {
    Deck deck = findOwnedDeck(command.ownerId(), command.deckId());
    deck.update(
        command.title(), command.description(), command.tags(), command.defaultDesiredRetention());
    deckRepository.save(deck);
    auditPort.record(command.ownerId(), "deck.updated", "Deck", command.deckId().toString());
  }

  // ---------------------------------------------------------------
  // ArchiveDeckUseCase
  // ---------------------------------------------------------------

  @Override
  public void execute(ArchiveDeckUseCase.Command command) {
    Deck deck = findOwnedDeck(command.ownerId(), command.deckId());
    deck.archive();
    deckRepository.save(deck);
    auditPort.record(command.ownerId(), "deck.archived", "Deck", command.deckId().toString());
  }

  // ---------------------------------------------------------------
  // DeleteDeckUseCase
  // ---------------------------------------------------------------

  @Override
  public void execute(DeleteDeckUseCase.Command command) {
    // Ownership check first (throws NotFoundException if not owned)
    findOwnedDeck(command.ownerId(), command.deckId());
    deckRepository.deleteById(command.deckId());
    auditPort.record(command.ownerId(), "deck.deleted", "Deck", command.deckId().toString());
  }

  // ---------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------

  private Deck findOwnedDeck(OwnerId ownerId, DeckId deckId) {
    Deck deck =
        deckRepository
            .findById(deckId)
            .orElseThrow(() -> new NotFoundException("Deck", deckId.toString()));
    if (!deck.getOwnerId().equals(ownerId)) {
      // Information hiding: reveal nothing to callers about resources they don't own
      throw new NotFoundException("Deck", deckId.toString());
    }
    return deck;
  }
}
