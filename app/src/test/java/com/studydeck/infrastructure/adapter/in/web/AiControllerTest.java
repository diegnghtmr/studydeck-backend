package com.studydeck.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studydeck.domain.port.out.AiChatPort;
import com.studydeck.domain.port.out.AiSchemaValidationPort;
import com.studydeck.integration.AiTestConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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

/**
 * Web-layer integration test for {@link AiController}.
 *
 * <p>Verifies the controller speaks the openapi contract: the NESTED {@code
 * GenerateFlashcardsRequest} /{@code ImproveFlashcardRequest} request shapes are accepted, the
 * {@code GenerateFlashcardsResponse} ({@code generated[]}) shape is returned, unknown fields are
 * rejected with 400, and a missing chat provider degrades to a 503 {@code
 * application/problem+json}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(AiTestConfiguration.class)
@Testcontainers
@ActiveProfiles("dev")
class AiControllerTest {

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

  @MockitoBean AiChatPort aiChatPort;
  @MockitoBean AiSchemaValidationPort aiSchemaValidationPort;

  MockMvc mockMvc;

  private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  private static final String VALID_FLASHCARD_JSON =
      """
      {
        "schemaVersion": "1.0",
        "deck": {"title": "Biology"},
        "notes": [
          {"noteType": "basic", "front": "What is mitosis?", "back": "Cell division", "tags": ["bio"]}
        ]
      }
      """;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor aiJwt() {
    return jwt()
        .jwt(j -> j.subject(OWNER_ID.toString()))
        .authorities(new SimpleGrantedAuthority("SCOPE_ai.generate"));
  }

  @Test
  void generate_acceptsNestedContractRequest_andReturnsGeneratedShape() throws Exception {
    when(aiChatPort.isAvailable()).thenReturn(true);
    when(aiChatPort.generateFlashcardsRaw(anyString(), any(), anyList(), anyInt(), any()))
        .thenReturn(VALID_FLASHCARD_JSON);
    when(aiSchemaValidationPort.validateAndReturn(anyString()))
        .thenAnswer(inv -> inv.getArgument(0));

    String body =
        """
        {"source": {"type": "text", "content": "Cell biology source text"},
         "preferredTypes": ["basic"], "maxItems": 5, "language": "en"}
        """;

    mockMvc
        .perform(
            post("/v1/ai/generate-flashcards")
                .with(aiJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.generated[0].noteType").value("basic"))
        .andExpect(jsonPath("$.generated[0].content.front").value("What is mitosis?"))
        .andExpect(jsonPath("$.generated[0].content.back").value("Cell division"))
        // content must NOT leak envelope fields
        .andExpect(jsonPath("$.generated[0].content.noteType").doesNotExist());
  }

  @Test
  void generate_unknownField_returns400() throws Exception {
    when(aiChatPort.isAvailable()).thenReturn(true);

    String body =
        """
        {"source": {"type": "text", "content": "text"}, "sourceText": "legacy-flat-field"}
        """;

    mockMvc
        .perform(
            post("/v1/ai/generate-flashcards")
                .with(aiJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void generate_missingSource_returns400() throws Exception {
    when(aiChatPort.isAvailable()).thenReturn(true);

    mockMvc
        .perform(
            post("/v1/ai/generate-flashcards")
                .with(aiJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"maxItems\": 5}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void generate_noChatProvider_returns503ProblemJson() throws Exception {
    when(aiChatPort.isAvailable()).thenReturn(false);

    String body = "{\"source\": {\"type\": \"text\", \"content\": \"text\"}}";

    mockMvc
        .perform(
            post("/v1/ai/generate-flashcards")
                .with(aiJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isServiceUnavailable())
        .andExpect(
            content_type ->
                org.assertj.core.api.Assertions.assertThat(
                        content_type.getResponse().getContentType())
                    .contains("application/problem+json"));
  }

  @Test
  void improve_acceptsContractRequest_andReturnsImprovedContent() throws Exception {
    String improvedJson = "{\"front\": \"Better Q?\", \"back\": \"Better A.\"}";
    when(aiChatPort.isAvailable()).thenReturn(true);
    when(aiChatPort.improveFlashcardRaw(anyString(), anyString(), anyString()))
        .thenReturn(improvedJson);
    when(aiSchemaValidationPort.validateAndReturn(anyString()))
        .thenAnswer(inv -> inv.getArgument(0));

    String body =
        """
        {"noteType": "basic", "content": {"front": "Q?", "back": "A."},
         "objective": "clarity", "preserveMeaning": true}
        """;

    mockMvc
        .perform(
            post("/v1/ai/improve-flashcard")
                .with(aiJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.noteType").value("basic"))
        .andExpect(jsonPath("$.content.front").value("Better Q?"));
  }

  @Test
  void improve_noChatProvider_returns503() throws Exception {
    when(aiChatPort.isAvailable()).thenReturn(false);

    String body =
        """
        {"noteType": "basic", "content": {"front": "Q?", "back": "A."}, "objective": "clarity"}
        """;

    mockMvc
        .perform(
            post("/v1/ai/improve-flashcard")
                .with(aiJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isServiceUnavailable());
  }
}
