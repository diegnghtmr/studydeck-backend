package com.studydeck.application.support;

import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.NoteId;
import com.studydeck.domain.model.NoteType;
import com.studydeck.domain.port.out.NoteHashRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** In-memory test double for {@link NoteHashRepository}. */
public final class InMemoryNoteHashRepository implements NoteHashRepository {

  /** Key: deckId#noteType → set of content hashes. */
  private final Map<String, Set<String>> hashStore = new HashMap<>();

  @Override
  public Set<String> findExistingHashes(DeckId deckId, NoteType noteType) {
    String key = key(deckId, noteType);
    return new HashSet<>(hashStore.getOrDefault(key, Set.of()));
  }

  @Override
  public void saveHash(NoteId noteId, String hash) {
    // We don't know deckId/noteType at this level in the in-memory stub,
    // so we store using a "noteId" key for test inspection.
    // The real adapter resolves deckId+noteType via the note row.
    noteHashes.put(noteId.value().toString(), hash);
  }

  // ---------------------------------------------------------------
  // Test helpers
  // ---------------------------------------------------------------

  private final Map<String, String> noteHashes = new HashMap<>();

  /**
   * Registers a hash as already present in the given deck+type scope (simulates an existing note).
   * Used in tests to set up preconditions.
   */
  public void addExistingHash(DeckId deckId, NoteType noteType, String hash) {
    hashStore.computeIfAbsent(key(deckId, noteType), k -> new HashSet<>()).add(hash);
  }

  public Map<String, String> savedNoteHashes() {
    return Map.copyOf(noteHashes);
  }

  public void clear() {
    hashStore.clear();
    noteHashes.clear();
  }

  private static String key(DeckId deckId, NoteType noteType) {
    return deckId.value() + "#" + noteType.name();
  }
}
