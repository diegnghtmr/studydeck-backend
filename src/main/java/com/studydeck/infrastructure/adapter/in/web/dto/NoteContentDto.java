package com.studydeck.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Sealed hierarchy of note content DTOs for the API layer.
 *
 * <p>Instances are NOT created directly by Jackson deserialization. The request body uses {@link
 * com.fasterxml.jackson.databind.JsonNode} for the {@code content} field, and the web mapper
 * resolves the concrete subtype using the sibling {@code noteType} discriminator.
 *
 * <p>Matches the OpenAPI oneOf: BasicNoteContent | ReversedNoteContent | ClozeNoteContent |
 * MultipleChoiceNoteContent | FreeTextNoteContent.
 */
public sealed interface NoteContentDto
    permits NoteContentDto.BasicDto,
        NoteContentDto.ReversedDto,
        NoteContentDto.ClozeDto,
        NoteContentDto.MultipleChoiceDto,
        NoteContentDto.FreeTextDto {

  /**
   * Basic note content DTO: front + back.
   *
   * <p>Matches BasicNoteContent in OpenAPI.
   */
  @JsonIgnoreProperties(ignoreUnknown = false)
  record BasicDto(
      @NotBlank @Size(min = 1, max = 1000) String front,
      @NotBlank @Size(min = 1, max = 5000) String back)
      implements NoteContentDto {}

  /**
   * Reversed note content DTO: front + back (generates two cards).
   *
   * <p>Matches ReversedNoteContent in OpenAPI.
   */
  @JsonIgnoreProperties(ignoreUnknown = false)
  record ReversedDto(
      @NotBlank @Size(min = 1, max = 1000) String front,
      @NotBlank @Size(min = 1, max = 5000) String back)
      implements NoteContentDto {}

  /**
   * Cloze note content DTO.
   *
   * <p>Matches ClozeNoteContent in OpenAPI.
   */
  @JsonIgnoreProperties(ignoreUnknown = false)
  record ClozeDto(
      @NotBlank
          @Size(min = 1, max = 5000)
          @Pattern(
              regexp = ".*\\{\\{c[0-9]+::.+\\}\\}.*",
              message = "must contain at least one {{cN::...}} marker")
          String text)
      implements NoteContentDto {}

  /**
   * Multiple choice option DTO.
   *
   * <p>Matches MultipleChoiceOption in OpenAPI.
   */
  @JsonIgnoreProperties(ignoreUnknown = false)
  record MultipleChoiceOptionDto(
      @NotBlank @Pattern(regexp = "^[A-Z]$", message = "must be a single uppercase letter")
          String key,
      @NotBlank @Size(min = 1, max = 1000) String text) {}

  /**
   * Multiple choice note content DTO.
   *
   * <p>Matches MultipleChoiceNoteContent in OpenAPI.
   */
  @JsonIgnoreProperties(ignoreUnknown = false)
  record MultipleChoiceDto(
      @NotBlank @Size(min = 1, max = 2000) String question,
      @NotNull @Size(min = 4, max = 5) List<@Valid MultipleChoiceOptionDto> options,
      @NotNull @Size(min = 1, max = 1) List<@Pattern(regexp = "^[A-Z]$") String> correctOptionKeys,
      @Size(max = 5000) String explanation)
      implements NoteContentDto {}

  /**
   * Free text note content DTO.
   *
   * <p>Matches FreeTextNoteContent in OpenAPI.
   */
  @JsonIgnoreProperties(ignoreUnknown = false)
  record FreeTextDto(
      @NotBlank @Size(min = 1, max = 2000) String prompt,
      @NotBlank @Size(min = 1, max = 5000) String expectedAnswer,
      @Size(max = 2000) String gradingGuidance)
      implements NoteContentDto {}
}
