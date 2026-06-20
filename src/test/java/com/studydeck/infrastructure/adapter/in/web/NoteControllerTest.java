package com.studydeck.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studydeck.application.common.Page;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.Note;
import com.studydeck.domain.model.NoteContent;
import com.studydeck.domain.model.NoteId;
import com.studydeck.domain.port.in.CreateNoteUseCase;
import com.studydeck.domain.port.in.DeleteNoteUseCase;
import com.studydeck.domain.port.in.GetNoteQuery;
import com.studydeck.domain.port.in.ListCardsForNoteQuery;
import com.studydeck.domain.port.in.ListNotesQuery;
import com.studydeck.domain.port.in.UpdateNoteUseCase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/**
 * Web-layer integration test for {@link NoteController}.
 *
 * <p>Verifies: 201 Created with Location, polymorphic content deserialization, 400 problem+json on
 * validation failure.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
@ActiveProfiles("dev")
class NoteControllerTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg17")
          .withDatabaseName("studydeck_test")
          .withUsername("studydeck")
          .withPassword("studydeck");

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
  }

  @Autowired WebApplicationContext context;
  @Autowired ObjectMapper objectMapper;

  @MockitoBean
  @Qualifier("createNoteUseCase")
  CreateNoteUseCase createNote;

  @MockitoBean
  @Qualifier("listNotesQuery")
  ListNotesQuery listNotes;

  @MockitoBean
  @Qualifier("getNoteQuery")
  GetNoteQuery getNote;

  @MockitoBean
  @Qualifier("updateNoteUseCase")
  UpdateNoteUseCase updateNote;

  @MockitoBean
  @Qualifier("deleteNoteUseCase")
  DeleteNoteUseCase deleteNote;

  @MockitoBean
  @Qualifier("listCardsForNoteQuery")
  ListCardsForNoteQuery listCardsForNote;

  MockMvc mockMvc;

  private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID DECK_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID NOTE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  private Note fakeBasicNote() {
    return Note.reconstitute(
        new NoteId(NOTE_ID),
        new DeckId(DECK_ID),
        new NoteContent.Basic("What is Java?", "A JVM language"),
        List.of("java"),
        1,
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:00:00Z"));
  }

  @Test
  void createNote_basic_returns201WithLocationAndNoteTypeAsKebabCase() throws Exception {
    when(createNote.execute(any()))
        .thenReturn(new CreateNoteUseCase.Result(new NoteId(NOTE_ID), List.of()));
    when(getNote.execute(any())).thenReturn(fakeBasicNote());

    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "deckId", DECK_ID.toString(),
                "noteType", "basic",
                "tags", List.of("java"),
                "content", Map.of("front", "What is Java?", "back", "A JVM language")));

    mockMvc
        .perform(
            post("/v1/notes")
                .with(jwt().jwt(j -> j.subject(OWNER_ID.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/v1/notes/" + NOTE_ID))
        .andExpect(jsonPath("$.id").value(NOTE_ID.toString()))
        .andExpect(jsonPath("$.noteType").value("basic"))
        .andExpect(jsonPath("$.content.front").value("What is Java?"));
  }

  @Test
  void createNote_withoutDeckId_returns400ProblemJson() throws Exception {
    String body =
        objectMapper.writeValueAsString(
            Map.of("noteType", "basic", "content", Map.of("front", "Q", "back", "A")));

    mockMvc
        .perform(
            post("/v1/notes")
                .with(jwt().jwt(j -> j.subject(OWNER_ID.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Validation Failed"));
  }

  @Test
  void listNotes_returns200WithNoteTypeKebabCase() throws Exception {
    Page<Note> page = new Page<>(List.of(fakeBasicNote()), 0, 20, 1);
    when(listNotes.execute(any())).thenReturn(page);

    mockMvc
        .perform(get("/v1/notes").with(jwt().jwt(j -> j.subject(OWNER_ID.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].noteType").value("basic"))
        .andExpect(jsonPath("$.page.totalElements").value(1));
  }

  @Test
  void getNote_returns200() throws Exception {
    when(getNote.execute(any())).thenReturn(fakeBasicNote());

    mockMvc
        .perform(
            get("/v1/notes/{noteId}", NOTE_ID).with(jwt().jwt(j -> j.subject(OWNER_ID.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(NOTE_ID.toString()));
  }

  @Test
  void anyEndpoint_withoutAuth_returns401() throws Exception {
    mockMvc.perform(get("/v1/notes")).andExpect(status().isUnauthorized());
  }
}
