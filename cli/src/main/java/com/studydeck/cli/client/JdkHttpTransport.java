package com.studydeck.cli.client;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Production {@link HttpTransport} backed by {@link HttpClient}. */
public class JdkHttpTransport implements HttpTransport {

  private final HttpClient client;

  public JdkHttpTransport() {
    this.client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
  }

  @Override
  public HttpResponse<String> send(HttpRequest request) throws TransportException {
    try {
      return client.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new TransportException("HTTP request failed: " + e.getMessage(), e);
    }
  }
}
