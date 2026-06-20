package com.studydeck.cli.command;

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

/** Note management commands. */
@Command(
    name = "note",
    description = "Note management commands",
    subcommands = {NoteCommand.CreateCommand.class},
    mixinStandardHelpOptions = true)
public class NoteCommand implements Runnable {

  @Spec CommandSpec spec;

  @Override
  public void run() {
    spec.commandLine().usage(System.out);
  }

  // ── note create ────────────────────────────────────────────────────────────

  @Command(
      name = "create",
      description = "Create a note (POST /v1/notes)",
      mixinStandardHelpOptions = true)
  static class CreateCommand implements Runnable {

    @Option(
        names = {"--deck"},
        description = "Deck ID (UUID)",
        required = true)
    String deckId;

    @Option(
        names = {"--type"},
        description = "Note type (basic, reversed, cloze, multiple-choice, free-text)",
        required = true)
    String noteType;

    @Option(
        names = {"--front"},
        description = "Front side content (for basic/reversed note types)")
    String front;

    @Option(
        names = {"--back"},
        description = "Back side content (for basic/reversed note types)")
    String back;

    @Option(
        names = {"--text"},
        description = "Cloze text (for cloze note type)")
    String text;

    @Option(
        names = {"--tag"},
        description = "Tags",
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
      body.put("deckId", deckId);
      body.put("noteType", noteType);
      if (front != null) body.put("front", front);
      if (back != null) body.put("back", back);
      if (text != null) body.put("text", text);
      if (!tags.isEmpty()) body.put("tags", tags);

      ApiClient client = new ApiClient(baseUrl, resolvedToken);
      try {
        ApiResponse response = client.post("/v1/notes", body);
        if (response.isSuccess()) {
          if (jsonOutput) {
            printer.printJson(response.json());
          } else {
            String id = DeckCommand.fieldText(response.json(), "id");
            out.println(
                "Note created" + (id != null ? " (id: " + id + ")" : "") + " in deck " + deckId);
          }
        } else {
          printer.printError(
              "Failed to create note (" + response.status() + "): " + response.errorMessage());
          System.exit(1);
        }
      } catch (ApiException e) {
        printer.printError(e.getMessage());
        System.exit(1);
      }
    }
  }
}
