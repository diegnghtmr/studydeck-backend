package com.studydeck.cli.command;

import com.studydeck.cli.auth.TokenNotFoundException;
import com.studydeck.cli.auth.TokenResolver;
import com.studydeck.cli.client.ApiClient;
import com.studydeck.cli.client.ApiException;
import com.studydeck.cli.client.ApiResponse;
import com.studydeck.cli.output.OutputPrinter;
import java.io.IOException;
import java.io.PrintWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Auth command group: login, logout, whoami.
 *
 * <p>OAuth2 Device Authorization Grant flow (intended production path): 1. POST
 * /v1/auth/device/authorize → receive device_code + verification_uri 2. User opens verification_uri
 * in browser and approves 3. CLI polls /v1/auth/device/token until token arrives 4. Token stored in
 * ~/.config/studydeck/config
 *
 * <p>Development shortcut: set STUDYDECK_TOKEN env var or use --token flag directly.
 */
@Command(
    name = "auth",
    description = "Authentication commands",
    subcommands = {
      AuthCommand.LoginCommand.class,
      AuthCommand.LogoutCommand.class,
      AuthCommand.WhoAmICommand.class
    },
    mixinStandardHelpOptions = true)
public class AuthCommand implements Runnable {

  @Spec CommandSpec spec;

  @Override
  public void run() {
    spec.commandLine().usage(System.out);
  }

  // ── login ──────────────────────────────────────────────────────────────────

  @Command(
      name = "login",
      description = "Store an auth token for CLI use",
      mixinStandardHelpOptions = true)
  static class LoginCommand implements Runnable {

    @Option(
        names = {"--token"},
        description = "Bearer token to store",
        required = false)
    String loginToken;

    @Override
    public void run() {
      PrintWriter out = new PrintWriter(System.out, true);
      PrintWriter err = new PrintWriter(System.err, true);

      String tokenToStore = loginToken;
      if (tokenToStore == null || tokenToStore.isBlank()) {
        try {
          TokenResolver resolver = new TokenResolver();
          tokenToStore = resolver.resolve(null);
        } catch (TokenNotFoundException e) {
          err.println(
              "No token provided. Use --token <value> or set STUDYDECK_TOKEN environment"
                  + " variable.");
          err.println("For OAuth2 Device Grant (production): visit your IdP and obtain a token,");
          err.println("then run: studydeck auth login --token <your-token>");
          System.exit(1);
          return;
        }
      }

      try {
        new TokenResolver().storeToken(tokenToStore);
        out.println("Token stored successfully in ~/.config/studydeck/config");
      } catch (IOException e) {
        err.println("Failed to store token: " + e.getMessage());
        System.exit(1);
      }
    }
  }

  // ── logout ─────────────────────────────────────────────────────────────────

  @Command(
      name = "logout",
      description = "Remove stored auth token",
      mixinStandardHelpOptions = true)
  static class LogoutCommand implements Runnable {

    @Override
    public void run() {
      PrintWriter out = new PrintWriter(System.out, true);
      PrintWriter err = new PrintWriter(System.err, true);
      try {
        new TokenResolver().clearToken();
        out.println("Logged out. Token removed from ~/.config/studydeck/config");
      } catch (IOException e) {
        err.println("Failed to clear token: " + e.getMessage());
        System.exit(1);
      }
    }
  }

  // ── whoami ─────────────────────────────────────────────────────────────────

  @Command(
      name = "whoami",
      description = "Show the currently authenticated principal (GET /v1/auth/me)",
      mixinStandardHelpOptions = true)
  static class WhoAmICommand implements Runnable {

    @Option(names = "--json", description = "Output raw JSON", defaultValue = "false")
    boolean jsonOutput;

    @Option(
        names = "--base-url",
        description = "Backend URL",
        defaultValue = "${STUDYDECK_BASE_URL:-http://localhost:8080}")
    String baseUrl;

    @Option(names = "--token", description = "Bearer token", defaultValue = "${STUDYDECK_TOKEN:-}")
    String token;

    @Override
    public void run() {
      PrintWriter out = new PrintWriter(System.out, true);
      PrintWriter err = new PrintWriter(System.err, true);
      OutputPrinter printer = new OutputPrinter(out, err, jsonOutput);

      String resolvedToken;
      try {
        resolvedToken = new TokenResolver().resolve(token);
      } catch (TokenNotFoundException e) {
        printer.printError(e.getMessage());
        System.exit(1);
        return;
      }

      ApiClient client = new ApiClient(baseUrl, resolvedToken);
      try {
        ApiResponse response = client.get("/v1/auth/me");
        if (response.isSuccess()) {
          if (jsonOutput) {
            printer.printJson(response.json());
          } else {
            String displayName = extractField(response, "displayName", "username", "sub", "email");
            String email = extractField(response, "email");
            String id = extractField(response, "id", "sub");
            out.println("Authenticated as: " + displayName);
            if (email != null) out.println("Email: " + email);
            if (id != null) out.println("ID: " + id);
          }
        } else if (response.isUnauthorized()) {
          printer.printError("Not authenticated. Token may be invalid or expired.");
          System.exit(1);
        } else {
          printer.printError("API error (" + response.status() + "): " + response.errorMessage());
          System.exit(1);
        }
      } catch (ApiException e) {
        printer.printError(e.getMessage());
        System.exit(1);
      }
    }

    private String extractField(ApiResponse response, String... fieldNames) {
      if (response.json() == null) return null;
      for (String field : fieldNames) {
        var node = response.json().get(field);
        if (node != null && !node.isNull()) return node.asText();
      }
      return null;
    }
  }
}
