package com.studydeck.infrastructure.adapter.in.web.mapper;

import com.studydeck.application.common.Page;
import com.studydeck.domain.model.Deck;
import com.studydeck.infrastructure.adapter.in.web.dto.DeckResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.PageMetaResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.PagedResponse;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps domain {@link Deck} and {@link Page Deck pages} to REST response DTOs.
 *
 * <p>Pure translation — no business logic.
 */
@Component
public class DeckWebMapper {

  public DeckResponse toResponse(Deck deck) {
    return new DeckResponse(
        deck.getId().value(),
        deck.getTitle(),
        deck.getDescription(),
        List.copyOf(deck.getTags()),
        deck.isArchived(),
        deck.getDefaultDesiredRetention(),
        deck.getCreatedAt(),
        deck.getUpdatedAt());
  }

  public PagedResponse<DeckResponse> toPagedResponse(Page<Deck> page) {
    List<DeckResponse> items = page.content().stream().map(this::toResponse).toList();
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
