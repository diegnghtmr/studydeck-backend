package com.studydeck.infrastructure.adapter.in.web;

import com.studydeck.domain.port.in.ListNoteTypesQuery;
import com.studydeck.infrastructure.adapter.in.web.dto.NoteTypeDescriptorResponse;
import com.studydeck.infrastructure.adapter.in.web.dto.NoteTypeListResponse;
import com.studydeck.infrastructure.adapter.in.web.mapper.NoteTypeMapper;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driving adapter — REST controller for NoteType catalog.
 *
 * <p>Serves the static list of supported note types with their field schemas.
 */
@RestController
@RequestMapping("/v1/note-types")
class NoteTypeController {

  private final ListNoteTypesQuery listNoteTypes;
  private final NoteTypeMapper noteTypeMapper;

  NoteTypeController(ListNoteTypesQuery listNoteTypes, NoteTypeMapper noteTypeMapper) {
    this.listNoteTypes = listNoteTypes;
    this.noteTypeMapper = noteTypeMapper;
  }

  @GetMapping
  ResponseEntity<NoteTypeListResponse> listNoteTypes() {
    List<ListNoteTypesQuery.NoteTypeDescriptor> descriptors = listNoteTypes.execute();
    List<NoteTypeDescriptorResponse> items =
        descriptors.stream()
            .map(
                d ->
                    new NoteTypeDescriptorResponse(
                        noteTypeMapper.toDto(d.noteType()),
                        d.label(),
                        d.fields().stream().map(ListNoteTypesQuery.FieldDescriptor::name).toList(),
                        d.description()))
            .toList();
    return ResponseEntity.ok(new NoteTypeListResponse(items));
  }
}
