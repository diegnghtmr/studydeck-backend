package com.studydeck.infrastructure.adapter.in.web;

import com.studydeck.application.common.Page;
import com.studydeck.application.common.PageRequest;
import com.studydeck.domain.model.Card;
import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.Note;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.DeleteCardUseCase;
import com.studydeck.domain.port.in.GetCardQuery;
import com.studydeck.domain.port.in.GetNoteQuery;
import com.studydeck.domain.port.in.ListCardsQuery;
import com.studydeck.domain.port.in.ListDueCardsQuery;
import com.studydeck.domain.port.in.UpdateCardUseCase;
import com.studydeck.infrastructure.adapter.in.web.dto.CardListResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.CardPatchRequest;
import com.studydeck.infrastructure.adapter.in.web.dto.CardResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.PagedResponse;
import com.studydeck.infrastructure.adapter.in.web.mapper.CardWebMapper;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driving adapter — REST controller for Card operations.
 *
 * <p>Depends exclusively on input ports. No direct persistence access.
 */
@RestController
@RequestMapping("/v1/cards")
class CardController {

  private final ListCardsQuery listCards;
  private final GetCardQuery getCard;
  private final UpdateCardUseCase updateCard;
  private final DeleteCardUseCase deleteCard;
  private final GetNoteQuery getNote;
  private final ListDueCardsQuery listDueCards;
  private final CardWebMapper mapper;

  CardController(
      @Qualifier("listCardsQuery") ListCardsQuery listCards,
      @Qualifier("getCardQuery") GetCardQuery getCard,
      @Qualifier("updateCardUseCase") UpdateCardUseCase updateCard,
      @Qualifier("deleteCardUseCase") DeleteCardUseCase deleteCard,
      @Qualifier("getNoteQuery") GetNoteQuery getNote,
      @Qualifier("listDueCardsQuery") ListDueCardsQuery listDueCards,
      CardWebMapper mapper) {
    this.listCards = listCards;
    this.getCard = getCard;
    this.updateCard = updateCard;
    this.deleteCard = deleteCard;
    this.getNote = getNote;
    this.listDueCards = listDueCards;
    this.mapper = mapper;
  }

  @GetMapping("/due")
  ResponseEntity<CardListResponse> getDueCards(
      @RequestParam(required = false) UUID deckId,
      @RequestParam(defaultValue = "20") int limit,
      @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = new OwnerId(UUID.fromString(jwt.getSubject()));
    DeckId deckIdDomain = deckId != null ? new DeckId(deckId) : null;
    int effectiveLimit = Math.min(Math.max(1, limit), 200);
    List<Card> due =
        listDueCards.execute(new ListDueCardsQuery.Query(ownerId, deckIdDomain, effectiveLimit));
    return ResponseEntity.ok(mapper.toListResponse(due, deckId));
  }

  @GetMapping
  ResponseEntity<PagedResponse<CardResponse>> listCards(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) UUID deckId,
      @RequestParam(required = false) Boolean suspended,
      @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = new OwnerId(UUID.fromString(jwt.getSubject()));
    DeckId deckIdDomain = deckId != null ? new DeckId(deckId) : null;
    var query =
        new ListCardsQuery.Query(ownerId, deckIdDomain, suspended, PageRequest.of(page, size));
    Page<Card> result = listCards.execute(query);
    // Use provided deckId or null when filtering globally
    return ResponseEntity.ok(mapper.toPagedResponse(result, deckId));
  }

  @GetMapping("/{cardId}")
  ResponseEntity<CardResponse> getCard(
      @PathVariable UUID cardId, @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = new OwnerId(UUID.fromString(jwt.getSubject()));
    Card card = getCard.execute(new GetCardQuery.Query(ownerId, new CardId(cardId)));
    UUID deckId = resolveDeckId(card, ownerId);
    return ResponseEntity.ok(mapper.toResponse(card, deckId));
  }

  @PatchMapping("/{cardId}")
  ResponseEntity<CardResponse> patchCard(
      @PathVariable UUID cardId,
      @Valid @RequestBody CardPatchRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = new OwnerId(UUID.fromString(jwt.getSubject()));
    CardId cid = new CardId(cardId);

    if (request.suspended() != null) {
      updateCard.execute(new UpdateCardUseCase.Command(ownerId, cid, request.suspended()));
    }

    Card updated = getCard.execute(new GetCardQuery.Query(ownerId, cid));
    UUID deckId = resolveDeckId(updated, ownerId);
    return ResponseEntity.ok(mapper.toResponse(updated, deckId));
  }

  @DeleteMapping("/{cardId}")
  ResponseEntity<Void> deleteCard(@PathVariable UUID cardId, @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = new OwnerId(UUID.fromString(jwt.getSubject()));
    deleteCard.execute(new DeleteCardUseCase.Command(ownerId, new CardId(cardId)));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{cardId}/preview")
  ResponseEntity<CardPreviewResponse> previewCard(
      @PathVariable UUID cardId, @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = new OwnerId(UUID.fromString(jwt.getSubject()));
    Card card = getCard.execute(new GetCardQuery.Query(ownerId, new CardId(cardId)));
    // Basic preview — front payload text as prompt, answer payload text as back
    String front = extractText(card.getPromptPayload());
    String back = extractText(card.getAnswerPayload());
    return ResponseEntity.ok(new CardPreviewResponse(cardId, front, back, null));
  }

  // ---------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------

  private UUID resolveDeckId(Card card, OwnerId ownerId) {
    try {
      Note note = getNote.execute(new GetNoteQuery.Query(ownerId, card.getNoteId()));
      return note.getDeckId().value();
    } catch (Exception e) {
      return null;
    }
  }

  private String extractText(com.studydeck.domain.model.CardPayload payload) {
    return switch (payload) {
      case com.studydeck.domain.model.CardPayload.BasicPrompt p -> p.front();
      case com.studydeck.domain.model.CardPayload.BasicAnswer a -> a.back();
      case com.studydeck.domain.model.CardPayload.ClozePrompt p -> p.maskedText();
      case com.studydeck.domain.model.CardPayload.ClozeAnswer a -> a.fullText();
      case com.studydeck.domain.model.CardPayload.McqPrompt p ->
          p.question()
              + " | "
              + p.options().stream()
                  .map(o -> o.key() + ": " + o.text())
                  .collect(java.util.stream.Collectors.joining(", "));
      case com.studydeck.domain.model.CardPayload.McqAnswer a ->
          String.join(", ", a.correctOptionKeys());
      case com.studydeck.domain.model.CardPayload.FreeTextPrompt p -> p.prompt();
      case com.studydeck.domain.model.CardPayload.FreeTextAnswer a -> a.expectedAnswer();
    };
  }

  /** Minimal card preview response. */
  record CardPreviewResponse(UUID cardId, String front, String back, String hint) {}
}
