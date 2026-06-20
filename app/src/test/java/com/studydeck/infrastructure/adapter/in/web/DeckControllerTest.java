package com.studydeck.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studydeck.application.common.Page;
import com.studydeck.domain.model.Deck;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.ArchiveDeckUseCase;
import com.studydeck.domain.port.in.CreateDeckUseCase;
import com.studydeck.domain.port.in.DeleteDeckUseCase;
import com.studydeck.domain.port.in.GetDeckQuery;
import com.studydeck.domain.port.in.ListDecksQuery;
import com.studydeck.domain.port.in.UpdateDeckUseCase;
import com.studydeck.integration.AiTestConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
 * Web-layer integration test for {@link DeckController}.
 *
 * <p>Uses a full Spring context with Testcontainers PostgreSQL. Input ports are replaced by Mockito
 * beans to isolate the web layer behavior. JWT is provided via {@code
 * SecurityMockMvcRequestPostProcessors.jwt()}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(AiTestConfiguration.class)
@Testcontainers
@ActiveProfiles("dev")
class DeckControllerTest {

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
  @Qualifier("createDeckUseCase")
  CreateDeckUseCase createDeck;

  @MockitoBean
  @Qualifier("listDecksQuery")
  ListDecksQuery listDecks;

  @MockitoBean
  @Qualifier("getDeckQuery")
  GetDeckQuery getDeck;

  @MockitoBean
  @Qualifier("updateDeckUseCase")
  UpdateDeckUseCase updateDeck;

  @MockitoBean
  @Qualifier("archiveDeckUseCase")
  ArchiveDeckUseCase archiveDeck;

  @MockitoBean
  @Qualifier("deleteDeckUseCase")
  DeleteDeckUseCase deleteDeck;

  MockMvc mockMvc;

  private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID DECK_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  private Deck fakeDeck() {
    return Deck.reconstitute(
        new DeckId(DECK_ID),
        new OwnerId(OWNER_ID),
        "Test Deck",
        "A test deck",
        List.of("java"),
        0.9,
        false,
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:00:00Z"));
  }

  @Test
  void createDeck_returns201WithLocationAndBody() throws Exception {
    Deck deck = fakeDeck();
    when(createDeck.execute(any())).thenReturn(new DeckId(DECK_ID));
    when(getDeck.execute(any())).thenReturn(deck);

    String body =
        objectMapper.writeValueAsString(
            Map.of("title", "Test Deck", "description", "A test deck", "tags", List.of("java")));

    mockMvc
        .perform(
            post("/v1/decks")
                .with(jwt().jwt(j -> j.subject(OWNER_ID.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/v1/decks/" + DECK_ID))
        .andExpect(jsonPath("$.id").value(DECK_ID.toString()))
        .andExpect(jsonPath("$.title").value("Test Deck"));
  }

  @Test
  void listDecks_returns200WithPagedResponse() throws Exception {
    Deck deck = fakeDeck();
    Page<Deck> page = new Page<>(List.of(deck), 0, 20, 1);
    when(listDecks.execute(any())).thenReturn(page);

    mockMvc
        .perform(get("/v1/decks").with(jwt().jwt(j -> j.subject(OWNER_ID.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(DECK_ID.toString()))
        .andExpect(jsonPath("$.page.totalElements").value(1));
  }

  @Test
  void getDeck_returns200() throws Exception {
    when(getDeck.execute(any())).thenReturn(fakeDeck());

    mockMvc
        .perform(
            get("/v1/decks/{deckId}", DECK_ID).with(jwt().jwt(j -> j.subject(OWNER_ID.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(DECK_ID.toString()));
  }

  @Test
  void deleteDeck_hardDelete_returns204() throws Exception {
    when(getDeck.execute(any())).thenReturn(fakeDeck());

    mockMvc
        .perform(
            delete("/v1/decks/{deckId}", DECK_ID)
                .param("hardDelete", "true")
                .with(jwt().jwt(j -> j.subject(OWNER_ID.toString()))))
        .andExpect(status().isNoContent());
  }

  @Test
  void anyEndpoint_withoutAuth_returns401() throws Exception {
    mockMvc.perform(get("/v1/decks")).andExpect(status().isUnauthorized());
  }

  @Test
  void createDeck_withMissingTitle_returns400ProblemJson() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of("description", "no title"));

    mockMvc
        .perform(
            post("/v1/decks")
                .with(jwt().jwt(j -> j.subject(OWNER_ID.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Validation Failed"));
  }
}
