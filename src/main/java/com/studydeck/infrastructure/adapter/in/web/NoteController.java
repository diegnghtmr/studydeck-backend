package com.studydeck.infrastructure.adapter.in.web;

import com.studydeck.application.common.Page;
import com.studydeck.application.common.PageRequest;
import com.studydeck.domain.model.Card;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.Note;
import com.studydeck.domain.model.NoteContent;
import com.studydeck.domain.model.NoteId;
import com.studydeck.domain.model.NoteType;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.CreateNoteUseCase;
import com.studydeck.domain.port.in.DeleteNoteUseCase;
import com.studydeck.domain.port.in.GetNoteQuery;
import com.studydeck.domain.port.in.ListCardsForNoteQuery;
import com.studydeck.domain.port.in.ListNotesQuery;
import com.studydeck.domain.port.in.UpdateNoteUseCase;
import com.studydeck.infrastructure.adapter.in.web.dto.CardListResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.NoteCreateRequest;
import com.studydeck.infrastructure.adapter.in.web.dto.NotePatchRequest;
import com.studydeck.infrastructure.adapter.in.web.dto.NoteResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.NoteTypeValue;
import com.studydeck.infrastructure.adapter.in.web.dto.PagedResponse;
import com.studydeck.infrastructure.adapter.in.web.mapper.CardWebMapper;
import com.studydeck.infrastructure.adapter.in.web.mapper.NoteContentMapper;
import com.studydeck.infrastructure.adapter.in.web.mapper.NoteTypeMapper;
import com.studydeck.infrastructure.adapter.in.web.mapper.NoteWebMapper;
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
 * Driving adapter — REST controller for Note operations.
 *
 * <p>Depends exclusively on input ports. No direct persistence access.
 */
@RestController
@RequestMapping("/v1/notes")
class NoteController {

  private final CreateNoteUseCase createNote;
  private final ListNotesQuery listNotes;
  private final GetNoteQuery getNote;
  private final UpdateNoteUseCase updateNote;
  private final DeleteNoteUseCase deleteNote;
  private final ListCardsForNoteQuery listCardsForNote;
  private final NoteWebMapper noteMapper;
  private final NoteContentMapper contentMapper;
  private final NoteTypeMapper noteTypeMapper;
  private final CardWebMapper cardMapper;

  NoteController(
      @Qualifier("createNoteUseCase") CreateNoteUseCase createNote,
      @Qualifier("listNotesQuery") ListNotesQuery listNotes,
      @Qualifier("getNoteQuery") GetNoteQuery getNote,
      @Qualifier("updateNoteUseCase") UpdateNoteUseCase updateNote,
      @Qualifier("deleteNoteUseCase") DeleteNoteUseCase deleteNote,
      @Qualifier("listCardsForNoteQuery") ListCardsForNoteQuery listCardsForNote,
      NoteWebMapper noteMapper,
      NoteContentMapper contentMapper,
      NoteTypeMapper noteTypeMapper,
      CardWebMapper cardMapper) {
    this.createNote = createNote;
    this.listNotes = listNotes;
    this.getNote = getNote;
    this.updateNote = updateNote;
    this.deleteNote = deleteNote;
    this.listCardsForNote = listCardsForNote;
    this.noteMapper = noteMapper;
    this.contentMapper = contentMapper;
    this.noteTypeMapper = noteTypeMapper;
    this.cardMapper = cardMapper;
  }

  @PostMapping
  ResponseEntity<NoteResponse> createNote(
      @Valid @RequestBody NoteCreateRequest request, @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = new OwnerId(UUID.fromString(jwt.getSubject()));
    NoteContent content =
        contentMapper.toDomain(contentMapper.fromJson(request.content(), request.noteType()));
    var command =
        new CreateNoteUseCase.Command(
            ownerId, new DeckId(request.deckId()), content, request.tags());
    CreateNoteUseCase.Result result = createNote.execute(command);
    Note note = getNote.execute(new GetNoteQuery.Query(ownerId, result.noteId()));
    URI location = URI.create("/v1/notes/" + result.noteId().value());
    return ResponseEntity.created(location).body(noteMapper.toResponse(note));
  }

  @GetMapping
  ResponseEntity<PagedResponse<NoteResponse>> listNotes(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) UUID deckId,
      @RequestParam(required = false) NoteTypeValue noteType,
      @RequestParam(required = false) String tag,
      @RequestParam(required = false) String search,
      @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = new OwnerId(UUID.fromString(jwt.getSubject()));
    DeckId deckIdDomain = deckId != null ? new DeckId(deckId) : null;
    NoteType noteTypeDomain = noteType != null ? noteTypeMapper.toDomain(noteType) : null;
    var query =
        new ListNotesQuery.Query(
            ownerId, deckIdDomain, noteTypeDomain, tag, search, PageRequest.of(page, size));
    Page<Note> result = listNotes.execute(query);
    return ResponseEntity.ok(noteMapper.toPagedResponse(result));
  }

  @GetMapping("/{noteId}")
  ResponseEntity<NoteResponse> getNote(
      @PathVariable UUID noteId, @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = new OwnerId(UUID.fromString(jwt.getSubject()));
    Note note = getNote.execute(new GetNoteQuery.Query(ownerId, new NoteId(noteId)));
    return ResponseEntity.ok(noteMapper.toResponse(note));
  }

  @PatchMapping("/{noteId}")
  ResponseEntity<NoteResponse> patchNote(
      @PathVariable UUID noteId,
      @Valid @RequestBody NotePatchRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = new OwnerId(UUID.fromString(jwt.getSubject()));
    NoteId nid = new NoteId(noteId);

    // Resolve current note to maintain existing content when not patched
    Note current = getNote.execute(new GetNoteQuery.Query(ownerId, nid));
    NoteContent newContent;
    if (request.content() != null && !request.content().isNull()) {
      // Use the existing note's type as the discriminator for patch content
      NoteTypeValue existingType = noteTypeMapper.toDto(current.getNoteType());
      newContent = contentMapper.toDomain(contentMapper.fromJson(request.content(), existingType));
    } else {
      newContent = current.getContent();
    }
    List<String> newTags = request.tags() != null ? request.tags() : current.getTags();

    var command = new UpdateNoteUseCase.Command(ownerId, nid, newContent, newTags);
    updateNote.execute(command);

    Note updated = getNote.execute(new GetNoteQuery.Query(ownerId, nid));
    return ResponseEntity.ok(noteMapper.toResponse(updated));
  }

  @DeleteMapping("/{noteId}")
  ResponseEntity<Void> deleteNote(@PathVariable UUID noteId, @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = new OwnerId(UUID.fromString(jwt.getSubject()));
    deleteNote.execute(new DeleteNoteUseCase.Command(ownerId, new NoteId(noteId)));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{noteId}/cards")
  ResponseEntity<CardListResponse> listCardsForNote(
      @PathVariable UUID noteId, @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = new OwnerId(UUID.fromString(jwt.getSubject()));
    NoteId nid = new NoteId(noteId);
    List<Card> cards = listCardsForNote.execute(new ListCardsForNoteQuery.Query(ownerId, nid));
    // Resolve deckId from the note
    Note note = getNote.execute(new GetNoteQuery.Query(ownerId, nid));
    UUID deckId = note.getDeckId().value();
    return ResponseEntity.ok(cardMapper.toListResponse(cards, deckId));
  }
}
