package com.studydeck.infrastructure.adapter.in.web.mapper;

import com.studydeck.application.common.Page;
import com.studydeck.domain.model.Card;
import com.studydeck.infrastructure.adapter.in.web.dto.CardListResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.CardResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.PageMetaResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.PagedResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Maps domain {@link Card} and card pages to REST response DTOs.
 *
 * <p>Key mappings: Card.ordinal → position, Card.cardVariant → cardVariant, NoteType → kebab.
 * deckId is passed explicitly as it is not stored in the domain Card (it comes from the note's
 * deck).
 */
@Component
public class CardWebMapper {

  private final NoteTypeMapper noteTypeMapper;

  public CardWebMapper(NoteTypeMapper noteTypeMapper) {
    this.noteTypeMapper = noteTypeMapper;
  }

  public CardResponse toResponse(Card card, UUID deckId) {
    return new CardResponse(
        card.getId().value(),
        card.getNoteId().value(),
        deckId,
        noteTypeMapper.toDto(card.getNoteType()),
        card.getCardVariant(),
        card.getOrdinal(), // ordinal → position per contract
        card.isSuspended(),
        null, // dueAt — null in P1 (no FSRS)
        card.getCreatedAt(),
        card.getCreatedAt()); // updatedAt same as createdAt for now
  }

  public CardListResponse toListResponse(List<Card> cards, UUID deckId) {
    return new CardListResponse(cards.stream().map(c -> toResponse(c, deckId)).toList());
  }

  public PagedResponse<CardResponse> toPagedResponse(Page<Card> page, UUID deckId) {
    List<CardResponse> items = page.content().stream().map(c -> toResponse(c, deckId)).toList();
    long totalPages = page.totalPages();
    var meta =
        new PageMetaResponse(
            page.page(),
            page.size(),
            page.totalElements(),
            totalPages,
            page.hasNext(),
            page.page() > 0);
    return new PagedResponse<>(items, meta);
  }
}
