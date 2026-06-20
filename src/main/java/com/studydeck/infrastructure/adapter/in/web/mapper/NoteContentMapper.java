package com.studydeck.infrastructure.adapter.in.web.mapper;

import com.studydeck.domain.model.NoteContent;
import com.studydeck.infrastructure.adapter.in.web.dto.NoteContentDto;
import com.studydeck.infrastructure.adapter.in.web.dto.NoteTypeValue;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Maps between domain {@link NoteContent} sealed hierarchy and API {@link NoteContentDto} sealed
 * hierarchy.
 *
 * <p>Pure translation — no business logic. Domain validations fire in domain constructors.
 *
 * <p>Deserialization uses the {@code noteType} discriminator (since BasicNoteContent and
 * ReversedNoteContent share identical JSON field shapes and cannot be distinguished by deduction).
 */
@Component
public class NoteContentMapper {

  private final ObjectMapper objectMapper;

  public NoteContentMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Converts a raw JSON node to the appropriate NoteContentDto subtype using the noteType
   * discriminator.
   */
  public NoteContentDto fromJson(JsonNode node, NoteTypeValue noteType) {
    if (node == null || node.isNull()) {
      return null;
    }
    try {
      return switch (noteType) {
        case BASIC -> objectMapper.treeToValue(node, NoteContentDto.BasicDto.class);
        case REVERSED -> objectMapper.treeToValue(node, NoteContentDto.ReversedDto.class);
        case CLOZE -> objectMapper.treeToValue(node, NoteContentDto.ClozeDto.class);
        case MULTIPLE_CHOICE ->
            objectMapper.treeToValue(node, NoteContentDto.MultipleChoiceDto.class);
        case FREE_TEXT -> objectMapper.treeToValue(node, NoteContentDto.FreeTextDto.class);
      };
    } catch (tools.jackson.core.JacksonException e) {
      throw new IllegalArgumentException(
          "Invalid content for noteType " + noteType + ": " + e.getMessage(), e);
    }
  }

  public NoteContent toDomain(NoteContentDto dto) {
    return switch (dto) {
      case NoteContentDto.BasicDto b -> new NoteContent.Basic(b.front(), b.back());
      case NoteContentDto.ReversedDto r -> new NoteContent.Reversed(r.front(), r.back());
      case NoteContentDto.ClozeDto c -> new NoteContent.Cloze(c.text());
      case NoteContentDto.MultipleChoiceDto mc ->
          new NoteContent.MultipleChoice(
              mc.question(),
              mc.options().stream()
                  .map(o -> new NoteContent.MultipleChoice.Option(o.key(), o.text()))
                  .collect(Collectors.toList()),
              mc.correctOptionKeys(),
              mc.explanation());
      case NoteContentDto.FreeTextDto ft ->
          new NoteContent.FreeText(ft.prompt(), ft.expectedAnswer(), ft.gradingGuidance());
    };
  }

  public NoteContentDto toDto(NoteContent domain) {
    return switch (domain) {
      case NoteContent.Basic b -> new NoteContentDto.BasicDto(b.front(), b.back());
      case NoteContent.Reversed r -> new NoteContentDto.ReversedDto(r.front(), r.back());
      case NoteContent.Cloze c -> new NoteContentDto.ClozeDto(c.text());
      case NoteContent.MultipleChoice mc ->
          new NoteContentDto.MultipleChoiceDto(
              mc.question(),
              mc.options().stream()
                  .map(o -> new NoteContentDto.MultipleChoiceOptionDto(o.key(), o.text()))
                  .collect(Collectors.toList()),
              mc.correctOptionKeys(),
              mc.explanation());
      case NoteContent.FreeText ft ->
          new NoteContentDto.FreeTextDto(ft.prompt(), ft.expectedAnswer(), ft.gradingGuidance());
    };
  }
}
