package com.studydeck.infrastructure.adapter.in.web;

import com.studydeck.application.common.Page;
import com.studydeck.application.common.PageRequest;
import com.studydeck.domain.model.Deck;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.ArchiveDeckUseCase;
import com.studydeck.domain.port.in.CreateDeckUseCase;
import com.studydeck.domain.port.in.DeleteDeckUseCase;
import com.studydeck.domain.port.in.GetDeckQuery;
import com.studydeck.domain.port.in.GetDeckStatsQuery;
import com.studydeck.domain.port.in.GetDeckStatsQuery.DeckStatsResult;
import com.studydeck.domain.port.in.ListDecksQuery;
import com.studydeck.domain.port.in.UpdateDeckUseCase;
import com.studydeck.infrastructure.adapter.in.web.dto.DeckCreateRequest;
import com.studydeck.infrastructure.adapter.in.web.dto.DeckPatchRequest;
import com.studydeck.infrastructure.adapter.in.web.dto.DeckResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.DeckStatsResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.PagedResponse;
import com.studydeck.infrastructure.adapter.in.web.mapper.DeckWebMapper;
import jakarta.validation.Valid;
import java.net.URI;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driving adapter — REST controller for Deck operations.
 *
 * <p>Depends exclusively on input ports (use cases and queries). No direct persistence access.
 */
@RestController
@RequestMapping("/v1/decks")
class DeckController {

  private final CreateDeckUseCase createDeck;
  private final ListDecksQuery listDecks;
  private final GetDeckQuery getDeck;
  private final UpdateDeckUseCase updateDeck;
  private final ArchiveDeckUseCase archiveDeck;
  private final DeleteDeckUseCase deleteDeck;
  private final GetDeckStatsQuery getDeckStats;
  private final DeckWebMapper mapper;

  DeckController(
      @Qualifier("createDeckUseCase") CreateDeckUseCase createDeck,
      @Qualifier("listDecksQuery") ListDecksQuery listDecks,
      @Qualifier("getDeckQuery") GetDeckQuery getDeck,
      @Qualifier("updateDeckUseCase") UpdateDeckUseCase updateDeck,
      @Qualifier("archiveDeckUseCase") ArchiveDeckUseCase archiveDeck,
      @Qualifier("deleteDeckUseCase") DeleteDeckUseCase deleteDeck,
      @Qualifier("getDeckStatsQuery") GetDeckStatsQuery getDeckStats,
      DeckWebMapper mapper) {
    this.createDeck = createDeck;
    this.listDecks = listDecks;
    this.getDeck = getDeck;
    this.updateDeck = updateDeck;
    this.archiveDeck = archiveDeck;
    this.deleteDeck = deleteDeck;
    this.getDeckStats = getDeckStats;
    this.mapper = mapper;
  }

  @PostMapping
  ResponseEntity<DeckResponse> createDeck(
      @Valid @RequestBody DeckCreateRequest request, @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = new OwnerId(UUID.fromString(jwt.getSubject()));
    double retention =
        request.defaultDesiredRetention() != null ? request.defaultDesiredRetention() : 0.9;
    var command =
        new CreateDeckUseCase.Command(
            ownerId, request.title(), request.description(), request.tags(), retention);
    DeckId deckId = createDeck.execute(command);
    Deck deck = getDeck.execute(new GetDeckQuery.Query(ownerId, deckId));
    URI location = URI.create("/v1/decks/" + deckId.value());
    return ResponseEntity.created(location).body(mapper.toResponse(deck));
  }

  @GetMapping
  ResponseEntity<PagedResponse<DeckResponse>> listDecks(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) Boolean archived,
      @RequestParam(required = false) String search,
      @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = new OwnerId(UUID.fromString(jwt.getSubject()));
    boolean includeArchived = Boolean.TRUE.equals(archived);
    var query =
        new ListDecksQuery.Query(ownerId, includeArchived, search, PageRequest.of(page, size));
    Page<Deck> result = listDecks.execute(query);
    return ResponseEntity.ok(mapper.toPagedResponse(result));
  }

  @GetMapping("/{deckId}")
  ResponseEntity<DeckResponse> getDeck(
      @PathVariable UUID deckId, @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = new OwnerId(UUID.fromString(jwt.getSubject()));
    Deck deck = getDeck.execute(new GetDeckQuery.Query(ownerId, new DeckId(deckId)));
    return ResponseEntity.ok(mapper.toResponse(deck));
  }

  @PatchMapping("/{deckId}")
  ResponseEntity<DeckResponse> patchDeck(
      @PathVariable UUID deckId,
      @Valid @RequestBody DeckPatchRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = new OwnerId(UUID.fromString(jwt.getSubject()));
    DeckId did = new DeckId(deckId);

    if (Boolean.TRUE.equals(request.archived())) {
      archiveDeck.execute(new ArchiveDeckUseCase.Command(ownerId, did));
    } else {
      // Resolve current state for fields not provided in the patch
      Deck current = getDeck.execute(new GetDeckQuery.Query(ownerId, did));
      String title = request.title() != null ? request.title() : current.getTitle();
      String description =
          request.description() != null ? request.description() : current.getDescription();
      List<String> tags = request.tags() != null ? request.tags() : current.getTags();
      double retention =
          request.defaultDesiredRetention() != null
              ? request.defaultDesiredRetention()
              : current.getDefaultDesiredRetention();
      updateDeck.execute(
          new UpdateDeckUseCase.Command(ownerId, did, title, description, tags, retention));
    }

    Deck updated = getDeck.execute(new GetDeckQuery.Query(ownerId, did));
    return ResponseEntity.ok(mapper.toResponse(updated));
  }

  @GetMapping("/{deckId}/stats")
  ResponseEntity<DeckStatsResponse> getDeckStats(
      @PathVariable UUID deckId, @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = new OwnerId(UUID.fromString(jwt.getSubject()));
    DeckStatsResult stats =
        getDeckStats.execute(new GetDeckStatsQuery.Query(ownerId, new DeckId(deckId)));
    DeckStatsResponse response =
        new DeckStatsResponse(
            stats.deckId().value(),
            stats.totalNotes(),
            stats.totalCards(),
            stats.dueToday(),
            stats.reviewedToday(),
            stats.suspendedCards(),
            stats.againRate7d(),
            stats.averageRetention30d());
    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/{deckId}")
  ResponseEntity<Void> deleteDeck(
      @PathVariable UUID deckId,
      @RequestParam(defaultValue = "false") boolean hardDelete,
      @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = new OwnerId(UUID.fromString(jwt.getSubject()));
    DeckId did = new DeckId(deckId);
    if (hardDelete) {
      deleteDeck.execute(new DeleteDeckUseCase.Command(ownerId, did));
    } else {
      archiveDeck.execute(new ArchiveDeckUseCase.Command(ownerId, did));
    }
    return ResponseEntity.noContent().build();
  }
}
