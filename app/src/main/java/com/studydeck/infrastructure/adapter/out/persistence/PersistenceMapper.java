package com.studydeck.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.studydeck.domain.model.Card;
import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.CardPayload;
import com.studydeck.domain.model.Deck;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.Note;
import com.studydeck.domain.model.NoteContent;
import com.studydeck.domain.model.NoteId;
import com.studydeck.domain.model.NoteType;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.UserAccount;
import com.studydeck.domain.model.UserAccountStatus;
import java.math.BigDecimal;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mapper between domain models and JPA entities.
 *
 * <p>Handles JSONB serialization of sealed {@link NoteContent}/{@link CardPayload} hierarchies
 * using a dedicated {@link JsonMapper} (Jackson 3 / {@code tools.jackson}).
 *
 * <p><b>Storage decisions:</b>
 *
 * <ul>
 *   <li>Deck/Note tags stored as PostgreSQL {@code text[]} — simple, GIN-indexable, no join table.
 *   <li>NoteContent stored as JSONB: the {@code note_type} column discriminates the concrete type;
 *       no {@code @type} field needed inside the JSON. Deserialization dispatches on column value.
 *   <li>CardPayload stored as JSONB with a {@code @type} discriminator embedded in the JSON because
 *       both prompt and answer columns are typed as the sealed {@code CardPayload} interface
 *       without any external discriminator column.
 * </ul>
 */
class PersistenceMapper {

  private final JsonMapper jsonMapper;

  PersistenceMapper() {
    this.jsonMapper = buildJsonMapper();
  }

  // ---------------------------------------------------------------
  // Deck
  // ---------------------------------------------------------------

  Deck toDomain(DeckJpaEntity e) {
    return Deck.reconstitute(
        new DeckId(e.getId()),
        new OwnerId(e.getOwnerId()),
        e.getTitle(),
        e.getDescription(),
        e.getTags(),
        e.getDefaultDesiredRetention(),
        e.isArchived(),
        e.getIcon(),
        e.getColor(),
        e.getCreatedAt(),
        e.getUpdatedAt());
  }

  DeckJpaEntity toJpa(Deck deck) {
    DeckJpaEntity e = new DeckJpaEntity();
    e.setId(deck.getId().value());
    e.setOwnerId(deck.getOwnerId().value());
    e.setTitle(deck.getTitle());
    e.setDescription(deck.getDescription());
    e.setTags(deck.getTags());
    e.setDefaultDesiredRetention(deck.getDefaultDesiredRetention());
    e.setArchived(deck.isArchived());
    e.setIcon(deck.getIcon());
    e.setColor(deck.getColor());
    e.setCreatedAt(deck.getCreatedAt());
    e.setUpdatedAt(deck.getUpdatedAt());
    return e;
  }

  // ---------------------------------------------------------------
  // Note
  // ---------------------------------------------------------------

  Note toDomain(NoteJpaEntity e) {
    NoteContent content = deserializeNoteContent(e.getNoteType(), e.getContent());
    return Note.reconstitute(
        new NoteId(e.getId()),
        new DeckId(e.getDeckId()),
        content,
        e.getTags(),
        e.getVersion(),
        e.getCreatedAt(),
        e.getUpdatedAt());
  }

  NoteJpaEntity toJpa(Note note) {
    NoteJpaEntity e = new NoteJpaEntity();
    e.setId(note.getId().value());
    e.setDeckId(note.getDeckId().value());
    e.setNoteType(note.getNoteType().name());
    e.setContent(serializeNoteContent(note.getContent()));
    e.setTags(note.getTags());
    e.setVersion(note.getVersion());
    e.setCreatedAt(note.getCreatedAt());
    e.setUpdatedAt(note.getUpdatedAt());
    return e;
  }

  // ---------------------------------------------------------------
  // Card
  // ---------------------------------------------------------------

  Card toDomain(CardJpaEntity e) {
    CardPayload prompt = deserializeCardPayload(e.getPromptPayload());
    CardPayload answer = deserializeCardPayload(e.getAnswerPayload());
    return Card.reconstitute(
        new CardId(e.getId()),
        new NoteId(e.getNoteId()),
        NoteType.valueOf(e.getNoteType()),
        e.getCardVariant(),
        e.getOrdinal(),
        prompt,
        answer,
        e.isSuspended(),
        e.getCreatedAt());
  }

  CardJpaEntity toJpa(Card card) {
    CardJpaEntity e = new CardJpaEntity();
    e.setId(card.getId().value());
    e.setNoteId(card.getNoteId().value());
    e.setNoteType(card.getNoteType().name());
    e.setCardVariant(card.getCardVariant());
    e.setOrdinal(card.getOrdinal());
    e.setPromptPayload(serializeCardPayload(card.getPromptPayload()));
    e.setAnswerPayload(serializeCardPayload(card.getAnswerPayload()));
    e.setSuspended(card.isSuspended());
    e.setCreatedAt(card.getCreatedAt());
    return e;
  }

  // ---------------------------------------------------------------
  // UserAccount
  // ---------------------------------------------------------------

  UserAccount toDomain(UserAccountJpaEntity e) {
    return UserAccount.reconstitute(
        new OwnerId(e.getId()),
        e.getEmail(),
        e.getDisplayName(),
        UserAccountStatus.valueOf(e.getStatus()),
        e.getDailyGoal(),
        e.getDesiredRetention() != null ? e.getDesiredRetention().doubleValue() : 0.90,
        e.getNewCardsPerDay(),
        e.getLanguage() != null ? e.getLanguage() : "en",
        e.getTimezone() != null ? e.getTimezone() : "UTC",
        e.getCreatedAt(),
        e.getUpdatedAt());
  }

  UserAccountJpaEntity toJpa(UserAccount account) {
    UserAccountJpaEntity e = new UserAccountJpaEntity();
    e.setId(account.getId().value());
    e.setEmail(account.getEmail());
    e.setDisplayName(account.getDisplayName());
    e.setStatus(account.getStatus().name());
    e.setDailyGoal(account.getDailyGoal());
    e.setDesiredRetention(BigDecimal.valueOf(account.getDesiredRetention()));
    e.setNewCardsPerDay(account.getNewCardsPerDay());
    e.setLanguage(account.getLanguage());
    e.setTimezone(account.getTimezone());
    e.setCreatedAt(account.getCreatedAt());
    e.setUpdatedAt(account.getUpdatedAt());
    return e;
  }

  // ---------------------------------------------------------------
  // Bulk helpers
  // ---------------------------------------------------------------

  List<Note> toDomainNotes(List<NoteJpaEntity> entities) {
    return entities.stream().map(this::toDomain).toList();
  }

  List<Card> toDomainCards(List<CardJpaEntity> entities) {
    return entities.stream().map(this::toDomain).toList();
  }

  // ---------------------------------------------------------------
  // NoteContent JSONB — dispatched via note_type column
  // ---------------------------------------------------------------

  private String serializeNoteContent(NoteContent content) {
    try {
      return jsonMapper.writeValueAsString(content);
    } catch (JacksonException ex) {
      throw new PersistenceMappingException("Failed to serialize NoteContent", ex);
    }
  }

  private NoteContent deserializeNoteContent(String noteType, String json) {
    Class<? extends NoteContent> targetClass =
        switch (noteType) {
          case "BASIC" -> NoteContent.Basic.class;
          case "REVERSED" -> NoteContent.Reversed.class;
          case "CLOZE" -> NoteContent.Cloze.class;
          case "MULTIPLE_CHOICE" -> NoteContent.MultipleChoice.class;
          case "FREE_TEXT" -> NoteContent.FreeText.class;
          default -> throw new IllegalArgumentException("Unknown note type: " + noteType);
        };
    try {
      return jsonMapper.readValue(json, targetClass);
    } catch (JacksonException ex) {
      throw new PersistenceMappingException(
          "Failed to deserialize NoteContent of type " + noteType, ex);
    }
  }

  // ---------------------------------------------------------------
  // CardPayload JSONB — polymorphic via @type discriminator
  // ---------------------------------------------------------------

  private String serializeCardPayload(CardPayload payload) {
    try {
      return jsonMapper.writeValueAsString(payload);
    } catch (JacksonException ex) {
      throw new PersistenceMappingException("Failed to serialize CardPayload", ex);
    }
  }

  private CardPayload deserializeCardPayload(String json) {
    try {
      return jsonMapper.readValue(json, CardPayload.class);
    } catch (JacksonException ex) {
      throw new PersistenceMappingException("Failed to deserialize CardPayload", ex);
    }
  }

  // ---------------------------------------------------------------
  // Jackson 3 JsonMapper configuration
  // ---------------------------------------------------------------

  private static JsonMapper buildJsonMapper() {
    // Jackson 3: JsonMapper.builder() — java.time support is built-in (no separate module needed)
    return JsonMapper.builder()
        // @type discriminator for CardPayload sealed hierarchy
        .addMixIn(CardPayload.class, CardPayloadMixin.class)
        .build();
  }

  /**
   * Mix-in that adds a {@code @type} discriminator property to the {@link CardPayload} sealed
   * interface, enabling Jackson to serialize and deserialize each concrete subtype without
   * modifying domain classes.
   */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = CardPayload.BasicPrompt.class, name = "BasicPrompt"),
    @JsonSubTypes.Type(value = CardPayload.BasicAnswer.class, name = "BasicAnswer"),
    @JsonSubTypes.Type(value = CardPayload.ClozePrompt.class, name = "ClozePrompt"),
    @JsonSubTypes.Type(value = CardPayload.ClozeAnswer.class, name = "ClozeAnswer"),
    @JsonSubTypes.Type(value = CardPayload.McqPrompt.class, name = "McqPrompt"),
    @JsonSubTypes.Type(value = CardPayload.McqAnswer.class, name = "McqAnswer"),
    @JsonSubTypes.Type(value = CardPayload.FreeTextPrompt.class, name = "FreeTextPrompt"),
    @JsonSubTypes.Type(value = CardPayload.FreeTextAnswer.class, name = "FreeTextAnswer")
  })
  private interface CardPayloadMixin {}
}
