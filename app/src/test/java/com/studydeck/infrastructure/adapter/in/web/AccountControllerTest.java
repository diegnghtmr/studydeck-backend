package com.studydeck.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.UserAccount;
import com.studydeck.domain.port.in.DeleteAccountUseCase;
import com.studydeck.domain.port.in.ExportAccountUseCase;
import com.studydeck.integration.AiTestConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
 * Web-layer integration test for {@link AccountController}.
 *
 * <p>Uses a full Spring context with Testcontainers PostgreSQL. Input ports are replaced by Mockito
 * beans to isolate the web layer. JWT scope authorities are injected via the jwt() post-processor.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(AiTestConfiguration.class)
@Testcontainers
@ActiveProfiles("dev")
class AccountControllerTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg17")
          .withDatabaseName("studydeck_account_test")
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

  @MockitoBean
  @Qualifier("exportAccountUseCase")
  ExportAccountUseCase exportAccount;

  @MockitoBean
  @Qualifier("deleteAccountUseCase")
  DeleteAccountUseCase deleteAccount;

  MockMvc mockMvc;

  private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void export_withExportReadScope_returns200WithAccountData() throws Exception {
    OwnerId ownerId = new OwnerId(OWNER_ID);
    UserAccount account = UserAccount.provision(ownerId, "user@example.com", "Test User");
    ExportAccountUseCase.Result result =
        new ExportAccountUseCase.Result(account, List.of(), List.of(), Instant.now());

    when(exportAccount.execute(any())).thenReturn(result);

    mockMvc
        .perform(
            get("/v1/account/export")
                .with(
                    jwt()
                        .jwt(j -> j.subject(OWNER_ID.toString()))
                        .authorities(new SimpleGrantedAuthority("SCOPE_export.read"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.account.id").value(OWNER_ID.toString()))
        .andExpect(jsonPath("$.account.email").value("user@example.com"))
        .andExpect(jsonPath("$.decks").isArray())
        .andExpect(jsonPath("$.documents").isArray())
        .andExpect(jsonPath("$.exportedAt").isNotEmpty());
  }

  @Test
  void export_withoutExportReadScope_returns403() throws Exception {
    mockMvc
        .perform(
            get("/v1/account/export")
                .with(
                    jwt()
                        .jwt(j -> j.subject(OWNER_ID.toString()))
                        .authorities(new SimpleGrantedAuthority("SCOPE_study.read"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void delete_withStudyWriteScope_returns204() throws Exception {
    doNothing().when(deleteAccount).execute(any());

    mockMvc
        .perform(
            delete("/v1/account")
                .with(
                    jwt()
                        .jwt(j -> j.subject(OWNER_ID.toString()))
                        .authorities(new SimpleGrantedAuthority("SCOPE_study.write"))))
        .andExpect(status().isNoContent());
  }

  @Test
  void delete_withoutStudyWriteScope_returns403() throws Exception {
    mockMvc
        .perform(
            delete("/v1/account")
                .with(
                    jwt()
                        .jwt(j -> j.subject(OWNER_ID.toString()))
                        .authorities(new SimpleGrantedAuthority("SCOPE_study.read"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void export_withoutAuth_returns401() throws Exception {
    mockMvc.perform(get("/v1/account/export")).andExpect(status().isUnauthorized());
  }

  @Test
  void delete_withoutAuth_returns401() throws Exception {
    mockMvc.perform(delete("/v1/account")).andExpect(status().isUnauthorized());
  }
}
