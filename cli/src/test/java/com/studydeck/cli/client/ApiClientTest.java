package com.studydeck.cli.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ApiClient — verifies correct HTTP request construction and response parsing using
 * a stub {@link HttpTransport} (avoids mocking the sealed JDK HttpClient class).
 */
class ApiClientTest {

  /** Captured requests sent via the stub transport. */
  private final List<HttpRequest> capturedRequests = new ArrayList<>();

  private StubTransport stubTransport;
  private ApiClient apiClient;

  @BeforeEach
  void setUp() {
    capturedRequests.clear();
    stubTransport = new StubTransport(200, "{}", capturedRequests);
    apiClient = new ApiClient("http://localhost:8080", "test-bearer-token", stubTransport);
  }

  // ── GET ─────────────────────────────────────────────────────────────────────

  @Test
  void shouldSendGetRequestWithAuthorizationHeader() throws Exception {
    stubTransport.configure(200, "{\"id\":\"deck-1\"}");

    apiClient.get("/v1/decks");

    assertThat(capturedRequests).hasSize(1);
    HttpRequest sentRequest = capturedRequests.get(0);
    assertThat(sentRequest.method()).isEqualTo("GET");
    assertThat(sentRequest.uri().toString()).isEqualTo("http://localhost:8080/v1/decks");
    assertThat(sentRequest.headers().firstValue("Authorization"))
        .hasValue("Bearer test-bearer-token");
    assertThat(sentRequest.headers().firstValue("Accept")).hasValue("application/json");
  }

  @Test
  void shouldParseJsonResponseBodyOnGet() throws Exception {
    stubTransport.configure(200, "{\"id\":\"abc\",\"title\":\"Biology\"}");

    ApiResponse result = apiClient.get("/v1/decks/abc");

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.json()).isNotNull();
    assertThat(result.json().get("title").asText()).isEqualTo("Biology");
  }

  @Test
  void shouldHandleNonJsonResponseBody() throws Exception {
    stubTransport.configure(500, "Internal Server Error");

    ApiResponse result = apiClient.get("/v1/decks");

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.json()).isNull();
    assertThat(result.rawBody()).isEqualTo("Internal Server Error");
  }

  @Test
  void shouldHandleEmptyResponseBody() throws Exception {
    stubTransport.configure(204, "");

    ApiResponse result = apiClient.get("/v1/decks/abc");

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.json()).isNull();
  }

  // ── POST ────────────────────────────────────────────────────────────────────

  @Test
  void shouldSendPostRequestWithJsonBody() throws Exception {
    stubTransport.configure(201, "{\"id\":\"new-deck-id\"}");

    Map<String, String> body = Map.of("title", "My Deck");
    ApiResponse result = apiClient.post("/v1/decks", body);

    assertThat(capturedRequests).hasSize(1);
    HttpRequest sentRequest = capturedRequests.get(0);
    assertThat(sentRequest.method()).isEqualTo("POST");
    assertThat(sentRequest.uri().toString()).isEqualTo("http://localhost:8080/v1/decks");
    assertThat(sentRequest.headers().firstValue("Content-Type")).hasValue("application/json");
    assertThat(sentRequest.headers().firstValue("Authorization"))
        .hasValue("Bearer test-bearer-token");
    assertThat(result.isSuccess()).isTrue();
  }

  @Test
  void shouldReturnApiResponseWith201Status() throws Exception {
    stubTransport.configure(201, "{\"id\":\"xyz\"}");

    ApiResponse result = apiClient.post("/v1/decks", Map.of("title", "Test"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.status()).isEqualTo(201);
    assertThat(result.json().get("id").asText()).isEqualTo("xyz");
  }

  // ── DELETE ──────────────────────────────────────────────────────────────────

  @Test
  void shouldSendDeleteRequest() throws Exception {
    stubTransport.configure(204, "");

    ApiResponse result = apiClient.delete("/v1/decks/deck-1");

    assertThat(capturedRequests).hasSize(1);
    assertThat(capturedRequests.get(0).method()).isEqualTo("DELETE");
    assertThat(result.isSuccess()).isTrue();
  }

  // ── Error handling ──────────────────────────────────────────────────────────

  @Test
  void shouldThrowApiExceptionOnTransportError() throws Exception {
    stubTransport.configureToThrow(
        new TransportException("Connection refused", new RuntimeException()));

    assertThatThrownBy(() -> apiClient.get("/v1/decks"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("HTTP request failed");
  }

  // ── Base URL trailing slash ──────────────────────────────────────────────────

  @Test
  void shouldNormalizeBaseUrlWithTrailingSlash() throws Exception {
    ApiClient clientWithSlash = new ApiClient("http://localhost:8080/", "token", stubTransport);

    clientWithSlash.get("/v1/decks");

    assertThat(capturedRequests.get(0).uri().toString())
        .isEqualTo("http://localhost:8080/v1/decks");
  }

  // ── No token ────────────────────────────────────────────────────────────────

  @Test
  void shouldNotSendAuthorizationHeaderWhenTokenIsEmpty() throws Exception {
    ApiClient noTokenClient = new ApiClient("http://localhost:8080", "", stubTransport);
    stubTransport.configure(401, "{\"detail\":\"Unauthorized\"}");

    ApiResponse result = noTokenClient.get("/v1/auth/me");

    assertThat(capturedRequests.get(0).headers().firstValue("Authorization")).isEmpty();
    assertThat(result.isUnauthorized()).isTrue();
  }

  // ── Stub transport ───────────────────────────────────────────────────────────

  /**
   * Simple in-memory stub for {@link HttpTransport}. Records every request in the provided list and
   * returns a configurable response.
   */
  static class StubTransport implements HttpTransport {

    private int statusCode;
    private String responseBody;
    private TransportException exceptionToThrow;
    private final List<HttpRequest> captured;

    StubTransport(int statusCode, String responseBody, List<HttpRequest> captured) {
      this.statusCode = statusCode;
      this.responseBody = responseBody;
      this.captured = captured;
    }

    void configure(int statusCode, String responseBody) {
      this.statusCode = statusCode;
      this.responseBody = responseBody;
      this.exceptionToThrow = null;
    }

    void configureToThrow(TransportException exception) {
      this.exceptionToThrow = exception;
    }

    @Override
    public HttpResponse<String> send(HttpRequest request) throws TransportException {
      captured.add(request);
      if (exceptionToThrow != null) {
        throw exceptionToThrow;
      }
      int capturedStatus = statusCode;
      String capturedBody = responseBody;
      return new HttpResponse<>() {
        @Override
        public int statusCode() {
          return capturedStatus;
        }

        @Override
        public String body() {
          return capturedBody;
        }

        @Override
        public HttpRequest request() {
          return request;
        }

        @Override
        public java.util.Optional<HttpResponse<String>> previousResponse() {
          return java.util.Optional.empty();
        }

        @Override
        public java.net.http.HttpHeaders headers() {
          return HttpRequest.newBuilder().uri(request.uri()).build().headers();
        }

        @Override
        public java.net.http.HttpClient.Version version() {
          return java.net.http.HttpClient.Version.HTTP_1_1;
        }

        @Override
        public java.net.URI uri() {
          return request.uri();
        }

        @Override
        public java.util.Optional<javax.net.ssl.SSLSession> sslSession() {
          return java.util.Optional.empty();
        }
      };
    }
  }
}
