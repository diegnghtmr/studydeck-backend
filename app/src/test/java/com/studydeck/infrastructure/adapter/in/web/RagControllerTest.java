package com.studydeck.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studydeck.domain.model.AiProviderConfig;
import com.studydeck.domain.port.in.GetActiveUserAiProviderQuery;
import com.studydeck.domain.port.out.AiChatPort;
import com.studydeck.integration.AiTestConfiguration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
 * Web-layer integration test for {@link RagController}.
 *
 * <p>Verifies that {@code providerOverride} has been removed (A-8 refactor), the 503
 * AiChatUnavailable path is preserved when no active provider exists, and the RAG chat happy path
 * works with server-side provider resolution.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(AiTestConfiguration.class)
@Testcontainers
@ActiveProfiles("dev")
class RagControllerTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg17")
          .withDatabaseName("studydeck_rag_test")
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

  @MockitoBean
  @Qualifier("getActiveUserAiProviderQuery")
  GetActiveUserAiProviderQuery getActiveUserAiProviderQuery;

  MockMvc mockMvc;

  private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor ragJwt() {
    return jwt()
        .jwt(j -> j.subject(OWNER_ID.toString()))
        .authorities(new SimpleGrantedAuthority("SCOPE_rag.query"));
  }

  // ---------------------------------------------------------------
  // POST /v1/rag/chat — happy path
  // ---------------------------------------------------------------

  @Test
  void ragChat_withActiveProvider_returns200WithAnswer() throws Exception {
    when(aiChatPort.isAvailable()).thenReturn(true);
    when(getActiveUserAiProviderQuery.execute(any()))
        .thenReturn(
            Optional.of(new AiProviderConfig("https://api.openai.com", "sk-testkey", "gpt-4o")));
    when(aiChatPort.ragChat(any(), any(), any(), any()))
        .thenReturn(new AiChatPort.RagAnswer("Paris is the capital.", List.of()));

    String body =
        """
        {"message": "What is the capital of France?", "topK": 5}
        """;

    mockMvc
        .perform(
            post("/v1/rag/chat")
                .with(ragJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.answer").value("Paris is the capital."));
  }

  // ---------------------------------------------------------------
  // POST /v1/rag/chat — providerOverride rejected (A-8: field removed)
  // ---------------------------------------------------------------

  @Test
  void ragChat_providerOverrideField_returns400_fieldRemovedInA8() throws Exception {
    when(aiChatPort.isAvailable()).thenReturn(true);

    String body =
        """
        {"message": "question",
         "providerOverride": {"baseUrl":"https://api.openai.com","apiKey":"sk-x","model":"gpt-4o"}}
        """;

    mockMvc
        .perform(
            post("/v1/rag/chat")
                .with(ragJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  // ---------------------------------------------------------------
  // POST /v1/rag/chat — 503 AiChatUnavailable preserved
  // ---------------------------------------------------------------

  @Test
  void ragChat_noActiveProvider_andNoGlobalProvider_returns503() throws Exception {
    when(getActiveUserAiProviderQuery.execute(any())).thenReturn(Optional.empty());
    when(aiChatPort.isAvailable()).thenReturn(false);

    String body =
        """
        {"message": "What is the capital?", "topK": 5}
        """;

    mockMvc
        .perform(
            post("/v1/rag/chat")
                .with(ragJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isServiceUnavailable());
  }

  // ---------------------------------------------------------------
  // POST /v1/rag/search — unchanged path (no providerOverride there)
  // ---------------------------------------------------------------

  @Test
  void ragSearch_basicQuery_returns200WithHits() throws Exception {
    String body =
        """
        {"query": "biology", "topK": 3}
        """;

    mockMvc
        .perform(
            post("/v1/rag/search")
                .with(ragJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hits").isArray());
  }
}
