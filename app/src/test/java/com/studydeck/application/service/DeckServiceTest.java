package com.studydeck.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studydeck.application.common.Page;
import com.studydeck.application.common.PageRequest;
import com.studydeck.application.exception.NotFoundException;
import com.studydeck.application.support.FixedClockPort;
import com.studydeck.application.support.InMemoryAuditEventPort;
import com.studydeck.application.support.InMemoryDeckRepository;
import com.studydeck.application.support.SequentialIdGenerator;
import com.studydeck.domain.model.Deck;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.ArchiveDeckUseCase;
import com.studydeck.domain.port.in.CreateDeckUseCase;
import com.studydeck.domain.port.in.DeleteDeckUseCase;
import com.studydeck.domain.port.in.GetDeckQuery;
import com.studydeck.domain.port.in.ListDecksQuery;
import com.studydeck.domain.port.in.UpdateDeckUseCase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Use-case tests for Deck services — plain Java, no Spring. */
class DeckServiceTest {

  private InMemoryDeckRepository deckRepo;
  private InMemoryAuditEventPort auditPort;
  private SequentialIdGenerator idGen;

  // Services under test — injected after implementation is written
  private CreateDeckUseCase createDeck;
  private ListDecksQuery listDecks;
  private GetDeckQuery getDeck;
  private UpdateDeckUseCase updateDeck;
  private ArchiveDeckUseCase archiveDeck;
  private DeleteDeckUseCase deleteDeck;

  private final OwnerId alice = OwnerId.generate();
  private final OwnerId bob = OwnerId.generate();

  @BeforeEach
  void setUp() {
    deckRepo = new InMemoryDeckRepository();
    auditPort = new InMemoryAuditEventPort();
    idGen = new SequentialIdGenerator();
    FixedClockPort clock = FixedClockPort.epoch();

    DeckService sut = new DeckService(deckRepo, auditPort, idGen, clock);
    createDeck = sut;
    listDecks = sut;
    getDeck = sut;
    updateDeck = sut;
    archiveDeck = sut;
    deleteDeck = sut;
  }

  // ---------------------------------------------------------------
  // CREATE
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("CreateDeck")
  class CreateDeckTests {

    @Test
    @DisplayName("creates a deck and persists it")
    void createsDeckAndPersistsIt() {
      UUID fixedId = UUID.randomUUID();
      idGen.enqueue(fixedId);

      DeckId returned =
          createDeck.execute(new CreateDeckUseCase.Command(alice, "Java Basics", null));

      assertThat(returned.value()).isEqualTo(fixedId);
      assertThat(deckRepo.size()).isEqualTo(1);
      Deck persisted = deckRepo.findById(returned).orElseThrow();
      assertThat(persisted.getTitle()).isEqualTo("Java Basics");
      assertThat(persisted.getOwnerId()).isEqualTo(alice);
      assertThat(persisted.isArchived()).isFalse();
    }

    @Test
    @DisplayName("creates a deck with tags and retention")
    void createsDeckWithTagsAndRetention() {
      DeckId returned =
          createDeck.execute(
              new CreateDeckUseCase.Command(alice, "Java", null, List.of("java"), 0.85));

      Deck persisted = deckRepo.findById(returned).orElseThrow();
      assertThat(persisted.getTags()).containsExactly("java");
      assertThat(persisted.getDefaultDesiredRetention()).isEqualTo(0.85);
    }

    @Test
    @DisplayName("emits audit event deck.created")
    void emitsAuditEvent() {
      createDeck.execute(new CreateDeckUseCase.Command(alice, "Deck A", "desc"));

      assertThat(auditPort.hasAction("deck.created")).isTrue();
      assertThat(auditPort.recorded().getFirst().actorId()).isEqualTo(alice);
    }

    @Test
    @DisplayName("rejects blank title")
    void rejectsBlankTitle() {
      assertThatThrownBy(() -> createDeck.execute(new CreateDeckUseCase.Command(alice, "  ", null)))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects title exceeding 120 chars")
    void rejectsLongTitle() {
      String longTitle = "x".repeat(121);
      assertThatThrownBy(
              () -> createDeck.execute(new CreateDeckUseCase.Command(alice, longTitle, null)))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  // ---------------------------------------------------------------
  // LIST
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("ListDecks")
  class ListDecksTests {

    @Test
    @DisplayName("returns only decks owned by the caller")
    void returnsOnlyCallerDecks() {
      createDeck.execute(new CreateDeckUseCase.Command(alice, "Alice Deck 1", null));
      createDeck.execute(new CreateDeckUseCase.Command(alice, "Alice Deck 2", null));
      createDeck.execute(new CreateDeckUseCase.Command(bob, "Bob Deck", null));

      Page<Deck> page =
          listDecks.execute(
              new ListDecksQuery.Query(alice, false, null, PageRequest.defaultPage()));

      assertThat(page.content()).hasSize(2);
      assertThat(page.totalElements()).isEqualTo(2);
      page.content().forEach(d -> assertThat(d.getOwnerId()).isEqualTo(alice));
    }

    @Test
    @DisplayName("excludes archived decks by default")
    void excludesArchivedByDefault() {
      createDeck.execute(new CreateDeckUseCase.Command(alice, "Active", null));
      DeckId archId = createDeck.execute(new CreateDeckUseCase.Command(alice, "Archived", null));
      archiveDeck.execute(new ArchiveDeckUseCase.Command(alice, archId));

      Page<Deck> page =
          listDecks.execute(
              new ListDecksQuery.Query(alice, false, null, PageRequest.defaultPage()));

      assertThat(page.content()).hasSize(1);
      assertThat(page.content().getFirst().getTitle()).isEqualTo("Active");
    }

    @Test
    @DisplayName("includes archived decks when requested")
    void includesArchivedWhenRequested() {
      createDeck.execute(new CreateDeckUseCase.Command(alice, "Active", null));
      DeckId archId = createDeck.execute(new CreateDeckUseCase.Command(alice, "Archived", null));
      archiveDeck.execute(new ArchiveDeckUseCase.Command(alice, archId));

      Page<Deck> page =
          listDecks.execute(new ListDecksQuery.Query(alice, true, null, PageRequest.defaultPage()));

      assertThat(page.content()).hasSize(2);
    }

    @Test
    @DisplayName("filters by search term")
    void filtersBySearch() {
      createDeck.execute(new CreateDeckUseCase.Command(alice, "Java Basics", null));
      createDeck.execute(new CreateDeckUseCase.Command(alice, "Python Advanced", null));

      Page<Deck> page =
          listDecks.execute(
              new ListDecksQuery.Query(alice, false, "java", PageRequest.defaultPage()));

      assertThat(page.content()).hasSize(1);
      assertThat(page.content().getFirst().getTitle()).isEqualTo("Java Basics");
    }

    @Test
    @DisplayName("paginates correctly")
    void paginatesCorrectly() {
      for (int i = 0; i < 5; i++) {
        createDeck.execute(new CreateDeckUseCase.Command(alice, "Deck " + i, null));
      }

      Page<Deck> firstPage =
          listDecks.execute(new ListDecksQuery.Query(alice, false, null, PageRequest.of(0, 2)));
      Page<Deck> secondPage =
          listDecks.execute(new ListDecksQuery.Query(alice, false, null, PageRequest.of(1, 2)));

      assertThat(firstPage.content()).hasSize(2);
      assertThat(secondPage.content()).hasSize(2);
      assertThat(firstPage.totalElements()).isEqualTo(5);
      assertThat(firstPage.totalPages()).isEqualTo(3);
      assertThat(firstPage.hasNext()).isTrue();
    }
  }

  // ---------------------------------------------------------------
  // GET
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("GetDeck")
  class GetDeckTests {

    @Test
    @DisplayName("returns own deck")
    void returnsOwnDeck() {
      DeckId id = createDeck.execute(new CreateDeckUseCase.Command(alice, "My Deck", null));

      Deck result = getDeck.execute(new GetDeckQuery.Query(alice, id));

      assertThat(result.getId()).isEqualTo(id);
    }

    @Test
    @DisplayName("throws NotFoundException for non-existent deck")
    void throwsNotFoundForNonExistentDeck() {
      DeckId unknown = DeckId.generate();

      assertThatThrownBy(() -> getDeck.execute(new GetDeckQuery.Query(alice, unknown)))
          .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("throws NotFoundException when deck belongs to another user (information hiding)")
    void throwsNotFoundForOtherUsersDeck() {
      DeckId bobDeck = createDeck.execute(new CreateDeckUseCase.Command(bob, "Bob's Deck", null));

      assertThatThrownBy(() -> getDeck.execute(new GetDeckQuery.Query(alice, bobDeck)))
          .isInstanceOf(NotFoundException.class);
    }
  }

  // ---------------------------------------------------------------
  // UPDATE
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("UpdateDeck")
  class UpdateDeckTests {

    @Test
    @DisplayName("updates an existing deck title")
    void updatesDeckTitle() {
      DeckId id = createDeck.execute(new CreateDeckUseCase.Command(alice, "Old Title", null));

      updateDeck.execute(new UpdateDeckUseCase.Command(alice, id, "New Title", null));

      Deck updated = deckRepo.findById(id).orElseThrow();
      assertThat(updated.getTitle()).isEqualTo("New Title");
    }

    @Test
    @DisplayName("updates deck with tags and retention")
    void updatesDeckTagsAndRetention() {
      DeckId id = createDeck.execute(new CreateDeckUseCase.Command(alice, "Deck", null));

      updateDeck.execute(
          new UpdateDeckUseCase.Command(alice, id, "Deck", null, List.of("tag1"), 0.80));

      Deck updated = deckRepo.findById(id).orElseThrow();
      assertThat(updated.getTags()).containsExactly("tag1");
      assertThat(updated.getDefaultDesiredRetention()).isEqualTo(0.80);
    }

    @Test
    @DisplayName("emits audit event deck.updated")
    void emitsAuditEvent() {
      DeckId id = createDeck.execute(new CreateDeckUseCase.Command(alice, "Deck", null));
      auditPort.clear();

      updateDeck.execute(new UpdateDeckUseCase.Command(alice, id, "Updated", null));

      assertThat(auditPort.hasAction("deck.updated")).isTrue();
    }

    @Test
    @DisplayName("throws NotFoundException when updating another user's deck")
    void throwsForOtherUsersDeck() {
      DeckId bobDeck = createDeck.execute(new CreateDeckUseCase.Command(bob, "Bob Deck", null));

      assertThatThrownBy(
              () ->
                  updateDeck.execute(
                      new UpdateDeckUseCase.Command(alice, bobDeck, "Stolen Title", null)))
          .isInstanceOf(NotFoundException.class);
    }
  }

  // ---------------------------------------------------------------
  // ARCHIVE
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("ArchiveDeck")
  class ArchiveDeckTests {

    @Test
    @DisplayName("archives a deck — idempotent")
    void archivesDeck() {
      DeckId id = createDeck.execute(new CreateDeckUseCase.Command(alice, "Deck", null));

      archiveDeck.execute(new ArchiveDeckUseCase.Command(alice, id));
      archiveDeck.execute(new ArchiveDeckUseCase.Command(alice, id)); // idempotent

      assertThat(deckRepo.findById(id).orElseThrow().isArchived()).isTrue();
    }

    @Test
    @DisplayName("emits audit event deck.archived")
    void emitsAuditEvent() {
      DeckId id = createDeck.execute(new CreateDeckUseCase.Command(alice, "Deck", null));
      auditPort.clear();

      archiveDeck.execute(new ArchiveDeckUseCase.Command(alice, id));

      assertThat(auditPort.hasAction("deck.archived")).isTrue();
    }

    @Test
    @DisplayName("throws NotFoundException when archiving another user's deck")
    void throwsForOtherUsersDeck() {
      DeckId bobDeck = createDeck.execute(new CreateDeckUseCase.Command(bob, "Bob Deck", null));

      assertThatThrownBy(() -> archiveDeck.execute(new ArchiveDeckUseCase.Command(alice, bobDeck)))
          .isInstanceOf(NotFoundException.class);
    }
  }

  // ---------------------------------------------------------------
  // DELETE
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("DeleteDeck")
  class DeleteDeckTests {

    @Test
    @DisplayName("deletes a deck from the repository")
    void deletesDeck() {
      DeckId id = createDeck.execute(new CreateDeckUseCase.Command(alice, "Deck", null));

      deleteDeck.execute(new DeleteDeckUseCase.Command(alice, id));

      assertThat(deckRepo.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("emits audit event deck.deleted")
    void emitsAuditEvent() {
      DeckId id = createDeck.execute(new CreateDeckUseCase.Command(alice, "Deck", null));
      auditPort.clear();

      deleteDeck.execute(new DeleteDeckUseCase.Command(alice, id));

      assertThat(auditPort.hasAction("deck.deleted")).isTrue();
    }

    @Test
    @DisplayName("throws NotFoundException when deleting another user's deck")
    void throwsForOtherUsersDeck() {
      DeckId bobDeck = createDeck.execute(new CreateDeckUseCase.Command(bob, "Bob Deck", null));

      assertThatThrownBy(() -> deleteDeck.execute(new DeleteDeckUseCase.Command(alice, bobDeck)))
          .isInstanceOf(NotFoundException.class);
    }
  }

  // ---------------------------------------------------------------
  // COMMAND VALIDATION
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("Command validation")
  class CommandValidationTests {

    @Test
    @DisplayName("CreateDeckCommand rejects null ownerId")
    void createCommandRejectsNullOwner() {
      assertThatThrownBy(() -> new CreateDeckUseCase.Command(null, "Name", null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("CreateDeckCommand rejects invalid retention")
    void createCommandRejectsInvalidRetention() {
      assertThatThrownBy(() -> new CreateDeckUseCase.Command(alice, "Name", null, null, 0.5))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ListDecksQuery rejects null pageRequest")
    void listQueryRejectsNullPageRequest() {
      assertThatThrownBy(() -> new ListDecksQuery.Query(alice, false, null, null))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
