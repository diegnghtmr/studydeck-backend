package com.studydeck.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.studydeck.cli.auth.TokenNotFoundException;
import com.studydeck.cli.auth.TokenResolver;
import com.studydeck.cli.client.ApiClient;
import com.studydeck.cli.client.ApiException;
import com.studydeck.cli.client.ApiResponse;
import com.studydeck.cli.output.OutputPrinter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** Study commands: get next due card and submit review. */
@Command(
    name = "study",
    description = "Study session commands",
    subcommands = {StudyCommand.NextCommand.class, StudyCommand.ReviewCommand.class},
    mixinStandardHelpOptions = true)
public class StudyCommand implements Runnable {

  @Spec CommandSpec spec;

  @Override
  public void run() {
    spec.commandLine().usage(System.out);
  }

  // ── study next ─────────────────────────────────────────────────────────────

  @Command(
      name = "next",
      description = "Get next due card for a deck (GET /v1/cards/due)",
      mixinStandardHelpOptions = true)
  static class NextCommand implements Runnable {

    @Option(
        names = {"--deck"},
        description = "Deck ID (UUID)",
        required = true)
    String deckId;

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

      ApiClient client = new ApiClient(baseUrl, resolvedToken);
      try {
        ApiResponse response = client.get("/v1/cards/due?deckId=" + deckId + "&limit=1");
        if (response.isSuccess()) {
          if (jsonOutput) {
            printer.printJson(response.json());
          } else {
            JsonNode items = response.json() != null ? response.json().get("items") : null;
            if (items == null || items.isEmpty()) {
              out.println("No cards due for study in this deck. Great work!");
            } else {
              JsonNode card = items.get(0);
              String id = DeckCommand.fieldText(card, "id");
              String dueAt = DeckCommand.fieldText(card, "dueAt");
              out.println("Next card:");
              out.println("  ID:     " + id);
              out.println("  Due at: " + dueAt);
              JsonNode payload = card.get("payload");
              if (payload != null) {
                out.println("  Front:  " + DeckCommand.fieldText(payload, "front"));
                out.println("  Back:   " + DeckCommand.fieldText(payload, "back"));
              }
              out.println();
              out.println(
                  "Review with: studydeck study review submit --card "
                      + id
                      + " --rating again|hard|good|easy");
            }
          }
        } else {
          printer.printError(
              "Failed to get due cards (" + response.status() + "): " + response.errorMessage());
          System.exit(1);
        }
      } catch (ApiException e) {
        printer.printError(e.getMessage());
        System.exit(1);
      }
    }
  }

  // ── study review submit ─────────────────────────────────────────────────────

  @Command(
      name = "review",
      description = "Review commands",
      subcommands = {StudyCommand.ReviewCommand.SubmitCommand.class},
      mixinStandardHelpOptions = true)
  static class ReviewCommand implements Runnable {

    @Spec CommandSpec spec;

    @Override
    public void run() {
      spec.commandLine().usage(System.out);
    }

    @Command(
        name = "submit",
        description = "Submit a card review rating (POST /v1/reviews)",
        mixinStandardHelpOptions = true)
    static class SubmitCommand implements Runnable {

      @Option(
          names = {"--card"},
          description = "Card ID (UUID)",
          required = true)
      String cardId;

      @Option(
          names = {"--rating"},
          description = "Review rating: again, hard, good, easy",
          required = true)
      String rating;

      @Option(names = "--json", description = "Output raw JSON", defaultValue = "false")
      boolean jsonOutput;

      @Option(names = "--base-url", defaultValue = "${STUDYDECK_BASE_URL:-http://localhost:8080}")
      String baseUrl;

      @Option(names = "--token", defaultValue = "${STUDYDECK_TOKEN:-}")
      String token;

      private static final java.util.Set<String> VALID_RATINGS =
          java.util.Set.of("again", "hard", "good", "easy");

      @Override
      public void run() {
        PrintWriter out = new PrintWriter(System.out, true);
        PrintWriter err = new PrintWriter(System.err, true);
        OutputPrinter printer = new OutputPrinter(out, err, jsonOutput);

        if (!VALID_RATINGS.contains(rating.toLowerCase())) {
          printer.printError(
              "Invalid rating '" + rating + "'. Must be one of: again, hard, good, easy");
          System.exit(1);
          return;
        }

        String resolvedToken;
        try {
          resolvedToken = new TokenResolver().resolve(token);
        } catch (TokenNotFoundException e) {
          printer.printError(e.getMessage());
          System.exit(1);
          return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("cardId", cardId);
        body.put("rating", rating.toLowerCase());

        ApiClient client = new ApiClient(baseUrl, resolvedToken);
        try {
          ApiResponse response = client.post("/v1/reviews", body);
          if (response.isSuccess()) {
            if (jsonOutput) {
              printer.printJson(response.json());
            } else {
              String nextDue = DeckCommand.fieldText(response.json(), "dueAt");
              if (nextDue == null && response.json() != null) {
                JsonNode nextState = response.json().get("nextState");
                if (nextState != null) nextDue = DeckCommand.fieldText(nextState, "dueAt");
              }
              out.println(
                  "Review submitted: "
                      + rating
                      + " for card "
                      + cardId
                      + (nextDue != null ? ". Next due: " + nextDue : ""));
            }
          } else {
            printer.printError(
                "Review failed (" + response.status() + "): " + response.errorMessage());
            System.exit(1);
          }
        } catch (ApiException e) {
          printer.printError(e.getMessage());
          System.exit(1);
        }
      }
    }
  }
}
