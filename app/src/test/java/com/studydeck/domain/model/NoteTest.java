package com.studydeck.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studydeck.domain.exception.DomainValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoteTest {

  private final NoteId id = NoteId.generate();
  private final DeckId deckId = DeckId.generate();
  private final NoteContent.Basic basicContent = new NoteContent.Basic("Front", "Back");

  @Test
  void createNoteWithValidArguments() {
    Note note = Note.create(id, deckId, basicContent, List.of("java", "basics"));
    assertThat(note.getId()).isEqualTo(id);
    assertThat(note.getDeckId()).isEqualTo(deckId);
    assertThat(note.getContent()).isEqualTo(basicContent);
    assertThat(note.getTags()).containsExactly("java", "basics");
    assertThat(note.getVersion()).isEqualTo(1);
    assertThat(note.getCreatedAt()).isNotNull();
    assertThat(note.getUpdatedAt()).isNotNull();
  }

  @Test
  void createNoteWithNullTagsDefaultsToEmptyList() {
    Note note = Note.create(id, deckId, basicContent, null);
    assertThat(note.getTags()).isEmpty();
  }

  @Test
  void noteTypeReflectsContentType() {
    Note basicNote = Note.create(id, deckId, new NoteContent.Basic("F", "B"), null);
    assertThat(basicNote.getNoteType()).isEqualTo(NoteType.BASIC);

    NoteId id2 = NoteId.generate();
    Note clozeNote =
        Note.create(id2, deckId, new NoteContent.Cloze("The {{c1::answer}} is here."), null);
    assertThat(clozeNote.getNoteType()).isEqualTo(NoteType.CLOZE);
  }

  @Test
  void nullIdIsRejected() {
    assertThatThrownBy(() -> Note.create(null, deckId, basicContent, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void nullDeckIdIsRejected() {
    assertThatThrownBy(() -> Note.create(id, null, basicContent, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void nullContentIsRejected() {
    assertThatThrownBy(() -> Note.create(id, deckId, null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void updateContentIncrementsVersion() {
    Note note = Note.create(id, deckId, basicContent, null);
    NoteContent.Basic newContent = new NoteContent.Basic("Updated front", "Updated back");
    note.updateContent(newContent);
    assertThat(note.getContent()).isEqualTo(newContent);
    assertThat(note.getVersion()).isEqualTo(2);
  }

  @Test
  void updateTagsReplacesTagList() {
    Note note = Note.create(id, deckId, basicContent, List.of("old-tag"));
    note.updateTags(List.of("new-tag-1", "new-tag-2"));
    assertThat(note.getTags()).containsExactly("new-tag-1", "new-tag-2");
  }

  @Test
  void tagsListIsImmutable() {
    Note note = Note.create(id, deckId, basicContent, List.of("tag1"));
    assertThatThrownBy(() -> note.getTags().add("hacked"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void nullContentUpdateIsRejected() {
    Note note = Note.create(id, deckId, basicContent, null);
    assertThatThrownBy(() -> note.updateContent(null))
        .isInstanceOf(DomainValidationException.class);
  }
}
