package com.studydeck.infrastructure.adapter.in.web.mapper;

import com.studydeck.application.common.Page;
import com.studydeck.domain.model.Note;
import com.studydeck.infrastructure.adapter.in.web.dto.NoteResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.PageMetaResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.PagedResponse;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps domain {@link Note} and {@link Page Note pages} to REST response DTOs.
 *
 * <p>Pure translation — no business logic.
 */
@Component
public class NoteWebMapper {

  private final NoteTypeMapper noteTypeMapper;
  private final NoteContentMapper noteContentMapper;

  public NoteWebMapper(NoteTypeMapper noteTypeMapper, NoteContentMapper noteContentMapper) {
    this.noteTypeMapper = noteTypeMapper;
    this.noteContentMapper = noteContentMapper;
  }

  public NoteResponse toResponse(Note note) {
    return new NoteResponse(
        note.getId().value(),
        note.getDeckId().value(),
        noteTypeMapper.toDto(note.getNoteType()),
        List.copyOf(note.getTags()),
        noteContentMapper.toDto(note.getContent()),
        note.getCreatedAt(),
        note.getUpdatedAt());
  }

  public PagedResponse<NoteResponse> toPagedResponse(Page<Note> page) {
    List<NoteResponse> items = page.content().stream().map(this::toResponse).toList();
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
