package com.studydeck.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.studydeck.cli.auth.TokenNotFoundException;
import com.studydeck.cli.auth.TokenResolver;
import com.studydeck.cli.client.ApiClient;
import com.studydeck.cli.client.ApiException;
import com.studydeck.cli.client.ApiResponse;
import com.studydeck.cli.output.OutputPrinter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** Deck management commands: create, ls. */
@Command(
    name = "deck",
    description = "Deck management commands",
    subcommands = {DeckCommand.CreateCommand.class, DeckCommand.ListCommand.class},
    mixinStandardHelpOptions = true)
public class DeckCommand implements Runnable {

  @Spec CommandSpec spec;

  @Override
  public void run() {
    spec.commandLine().usage(System.out);
  }

  // ── deck create ────────────────────────────────────────────────────────────

  @Command(
      name = "create",
      description = "Create a new deck (POST /v1/decks)",
      mixinStandardHelpOptions = true)
  static class CreateCommand implements Runnable {

    @Option(
        names = {"--title", "-t"},
        description = "Deck title",
        required = true)
    String title;

    @Option(
        names = {"--description", "-d"},
        description = "Deck description")
    String description;

    @Option(
        names = {"--tag"},
        description = "Tag(s) to assign to the deck",
        arity = "0..*")
    List<String> tags = new ArrayList<>();

    @Option(names = "--json", description = "Output raw JSON", defaultValue = "false")
    boolean jsonOutput;

    @Option(names = "--base-url", defaultValue = "${STUDYDECK_BASE_URL:-http://localhost:8080}")
    String baseUrl;

    @Option(names = "--token", defaultValue = "${STUDYDECK_TOKEN:-}")
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

      Map<String, Object> body = new HashMap<>();
      body.put("title", title);
      if (description != null) body.put("description", description);
      if (!tags.isEmpty()) body.put("tags", tags);

      ApiClient client = new ApiClient(baseUrl, resolvedToken);
      try {
        ApiResponse response = client.post("/v1/decks", body);
        if (response.isSuccess()) {
          if (jsonOutput) {
            printer.printJson(response.json());
          } else {
            String id = fieldText(response.json(), "id");
            out.println("Deck created: " + title + (id != null ? " (id: " + id + ")" : ""));
          }
        } else {
          printer.printError(
              "Failed to create deck (" + response.status() + "): " + response.errorMessage());
          System.exit(1);
        }
      } catch (ApiException e) {
        printer.printError(e.getMessage());
        System.exit(1);
      }
    }
  }

  // ── deck ls ────────────────────────────────────────────────────────────────

  @Command(name = "ls", description = "List decks (GET /v1/decks)", mixinStandardHelpOptions = true)
  static class ListCommand implements Runnable {

    @Option(names = "--json", description = "Output raw JSON", defaultValue = "false")
    boolean jsonOutput;

    @Option(names = "--base-url", defaultValue = "${STUDYDECK_BASE_URL:-http://localhost:8080}")
    String baseUrl;

    @Option(names = "--token", defaultValue = "${STUDYDECK_TOKEN:-}")
    String token;

    @Option(
        names = {"--page"},
        description = "Page number (0-based)",
        defaultValue = "0")
    int page;

    @Option(
        names = {"--size"},
        description = "Page size",
        defaultValue = "20")
    int size;

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
        ApiResponse response = client.get("/v1/decks?page=" + page + "&size=" + size);
        if (response.isSuccess()) {
          if (jsonOutput) {
            printer.printJson(response.json());
          } else {
            JsonNode items = response.json() != null ? response.json().get("items") : null;
            if (items == null || items.isEmpty()) {
              out.println("No decks found.");
            } else {
              out.printf("%-36s  %-40s  %s%n", "ID", "Title", "Tags");
              out.println("-".repeat(90));
              for (JsonNode deck : items) {
                String id = fieldText(deck, "id");
                String title = fieldText(deck, "title");
                JsonNode tagsNode = deck.get("tags");
                String tagsStr = tagsNode != null ? tagsNode.toString() : "[]";
                out.printf("%-36s  %-40s  %s%n", id, title, tagsStr);
              }
            }
          }
        } else {
          printer.printError(
              "Failed to list decks (" + response.status() + "): " + response.errorMessage());
          System.exit(1);
        }
      } catch (ApiException e) {
        printer.printError(e.getMessage());
        System.exit(1);
      }
    }
  }

  static String fieldText(JsonNode node, String field) {
    if (node == null) return null;
    JsonNode f = node.get(field);
    return (f != null && !f.isNull()) ? f.asText() : null;
  }
}
