package com.studydeck.application.support;

import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.Note;
import com.studydeck.domain.model.NoteId;
import com.studydeck.domain.model.NoteType;
import com.studydeck.domain.port.out.NoteRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory test double for {@link NoteRepository}. Thread-safe. */
public final class InMemoryNoteRepository implements NoteRepository {

  private final Map<NoteId, Note> store = new ConcurrentHashMap<>();

  @Override
  public void save(Note note) {
    store.put(note.getId(), note);
  }

  @Override
  public Optional<Note> findById(NoteId id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<Note> findAll(
      DeckId deckId, NoteType noteType, String tag, String search, int offset, int limit) {
    return store.values().stream()
        .filter(n -> deckId == null || n.getDeckId().equals(deckId))
        .filter(n -> noteType == null || n.getNoteType() == noteType)
        .filter(n -> tag == null || n.getTags().contains(tag))
        .filter(n -> matchesSearch(n, search))
        .sorted(
            java.util.Comparator.comparing(Note::getCreatedAt)
                .thenComparing(n -> n.getId().value()))
        .skip(offset)
        .limit(limit)
        .toList();
  }

  @Override
  public long countAll(DeckId deckId, NoteType noteType, String tag, String search) {
    return store.values().stream()
        .filter(n -> deckId == null || n.getDeckId().equals(deckId))
        .filter(n -> noteType == null || n.getNoteType() == noteType)
        .filter(n -> tag == null || n.getTags().contains(tag))
        .filter(n -> matchesSearch(n, search))
        .count();
  }

  @Override
  public void deleteById(NoteId id) {
    store.remove(id);
  }

  // --- Test helpers ---

  public int size() {
    return store.size();
  }

  public void clear() {
    store.clear();
  }

  public List<Note> all() {
    return new ArrayList<>(store.values());
  }

  // --- Private helpers ---

  private boolean matchesSearch(Note note, String search) {
    if (search == null || search.isBlank()) {
      return true;
    }
    // Simple in-memory search: look for text in the string representation of content
    String lower = search.toLowerCase();
    return note.getContent().toString().toLowerCase().contains(lower);
  }
}
