package com.studydeck.application.service;

import com.studydeck.application.common.Page;
import com.studydeck.application.exception.NotFoundException;
import com.studydeck.domain.model.Card;
import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.Deck;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.Note;
import com.studydeck.domain.model.NoteId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.DeleteCardUseCase;
import com.studydeck.domain.port.in.GetCardQuery;
import com.studydeck.domain.port.in.ListCardsQuery;
import com.studydeck.domain.port.in.UpdateCardUseCase;
import com.studydeck.domain.port.out.AuditEventPort;
import com.studydeck.domain.port.out.CardRepository;
import com.studydeck.domain.port.out.DeckRepository;
import com.studydeck.domain.port.out.NoteRepository;
import java.util.List;

/**
 * Application service implementing all Card use cases.
 *
 * <p>Framework-free: no Spring annotations. Wired as {@code @Bean} in {@code BeanConfiguration}.
 */
public final class CardService
    implements ListCardsQuery, GetCardQuery, UpdateCardUseCase, DeleteCardUseCase {

  private final DeckRepository deckRepository;
  private final NoteRepository noteRepository;
  private final CardRepository cardRepository;
  private final AuditEventPort auditPort;

  public CardService(
      DeckRepository deckRepository,
      NoteRepository noteRepository,
      CardRepository cardRepository,
      AuditEventPort auditPort) {
    this.deckRepository = deckRepository;
    this.noteRepository = noteRepository;
    this.cardRepository = cardRepository;
    this.auditPort = auditPort;
  }

  // ---------------------------------------------------------------
  // ListCardsQuery
  // ---------------------------------------------------------------

  @Override
  public Page<Card> execute(ListCardsQuery.Query query) {
    int offset = query.pageRequest().offset();
    int limit = query.pageRequest().size();
    int page = query.pageRequest().page();

    // For deck-level filtering we pass deckId; null means all decks.
    // The repository is responsible for resolving note→deck join.
    List<Card> content = cardRepository.findAll(query.deckId(), query.suspended(), offset, limit);
    long total = cardRepository.countAll(query.deckId(), query.suspended());

    // Filter to only cards the caller owns (via their note's deck)
    // When deckId is null we need to restrict to caller's decks.
    // We do a post-filter here at the application level for the in-memory case;
    // the JPA adapter will build the SQL join natively in B3.
    List<Card> owned = content.stream().filter(c -> isOwnedBy(c, query.ownerId())).toList();

    // Recount when we filtered (only relevant when deckId is null)
    long ownedTotal =
        query.deckId() == null ? countOwnedCards(query.ownerId(), query.suspended()) : total;

    return Page.of(owned, page, limit, ownedTotal);
  }

  // ---------------------------------------------------------------
  // GetCardQuery
  // ---------------------------------------------------------------

  @Override
  public Card execute(GetCardQuery.Query query) {
    return findOwnedCard(query.ownerId(), query.cardId());
  }

  // ---------------------------------------------------------------
  // UpdateCardUseCase
  // ---------------------------------------------------------------

  @Override
  public void execute(UpdateCardUseCase.Command command) {
    Card card = findOwnedCard(command.ownerId(), command.cardId());
    if (command.suspended()) {
      card.suspend();
    } else {
      card.unsuspend();
    }
    cardRepository.save(card);
    auditPort.record(command.ownerId(), "card.updated", "Card", command.cardId().toString());
  }

  // ---------------------------------------------------------------
  // DeleteCardUseCase
  // ---------------------------------------------------------------

  @Override
  public void execute(DeleteCardUseCase.Command command) {
    findOwnedCard(command.ownerId(), command.cardId()); // ownership check
    cardRepository.deleteById(command.cardId());
    auditPort.record(command.ownerId(), "card.deleted", "Card", command.cardId().toString());
  }

  // ---------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------

  private Card findOwnedCard(OwnerId ownerId, CardId cardId) {
    Card card =
        cardRepository
            .findById(cardId)
            .orElseThrow(() -> new NotFoundException("Card", cardId.toString()));
    if (!isOwnedBy(card, ownerId)) {
      throw new NotFoundException("Card", cardId.toString());
    }
    return card;
  }

  private boolean isOwnedBy(Card card, OwnerId ownerId) {
    NoteId noteId = card.getNoteId();
    Note note = noteRepository.findById(noteId).orElse(null);
    if (note == null) {
      return false;
    }
    DeckId deckId = note.getDeckId();
    Deck deck = deckRepository.findById(deckId).orElse(null);
    if (deck == null) {
      return false;
    }
    return deck.getOwnerId().equals(ownerId);
  }

  private long countOwnedCards(OwnerId ownerId, Boolean suspended) {
    // Full scan of all cards then filter — acceptable for in-memory/tests;
    // JPA adapter will implement with a proper join query in B3.
    List<Card> all = cardRepository.findAll(null, suspended, 0, Integer.MAX_VALUE);
    return all.stream().filter(c -> isOwnedBy(c, ownerId)).count();
  }
}
