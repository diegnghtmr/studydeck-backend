package com.studydeck.cli.client;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Port interface for HTTP transport. Allows test isolation without mocking sealed JDK classes.
 *
 * <p>The production implementation delegates to {@link java.net.http.HttpClient}. Tests use a
 * simple in-memory stub.
 */
@FunctionalInterface
public interface HttpTransport {

  /**
   * Sends an HTTP request and returns the response body as a String.
   *
   * @param request the request to send
   * @return response
   * @throws TransportException on network-level failure
   */
  HttpResponse<String> send(HttpRequest request) throws TransportException;
}
