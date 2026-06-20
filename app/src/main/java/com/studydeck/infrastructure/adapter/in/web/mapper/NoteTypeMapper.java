package com.studydeck.infrastructure.adapter.in.web.mapper;

import com.studydeck.domain.model.NoteType;
import com.studydeck.infrastructure.adapter.in.web.dto.NoteTypeValue;
import org.springframework.stereotype.Component;

/**
 * Maps between domain {@link NoteType} and API {@link NoteTypeValue} (kebab-case).
 *
 * <p>Mapping table: BASIC ↔ basic, REVERSED ↔ reversed, CLOZE ↔ cloze, MULTIPLE_CHOICE ↔
 * multiple-choice, FREE_TEXT ↔ free-text.
 */
@Component
public class NoteTypeMapper {

  public NoteTypeValue toDto(NoteType domain) {
    return switch (domain) {
      case BASIC -> NoteTypeValue.BASIC;
      case REVERSED -> NoteTypeValue.REVERSED;
      case CLOZE -> NoteTypeValue.CLOZE;
      case MULTIPLE_CHOICE -> NoteTypeValue.MULTIPLE_CHOICE;
      case FREE_TEXT -> NoteTypeValue.FREE_TEXT;
    };
  }

  public NoteType toDomain(NoteTypeValue dto) {
    return switch (dto) {
      case BASIC -> NoteType.BASIC;
      case REVERSED -> NoteType.REVERSED;
      case CLOZE -> NoteType.CLOZE;
      case MULTIPLE_CHOICE -> NoteType.MULTIPLE_CHOICE;
      case FREE_TEXT -> NoteType.FREE_TEXT;
    };
  }
}
