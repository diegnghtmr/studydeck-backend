package com.studydeck.cli;

import com.studydeck.cli.command.AuthCommand;
import com.studydeck.cli.command.DeckCommand;
import com.studydeck.cli.command.ExportCommand;
import com.studydeck.cli.command.ImportCommand;
import com.studydeck.cli.command.NoteCommand;
import com.studydeck.cli.command.StudyCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Root command for the StudyDeck CLI.
 *
 * <p>Global options --base-url, --token, and --json are defined per leaf command and read from
 * environment variables (STUDYDECK_BASE_URL, STUDYDECK_TOKEN) as default values.
 *
 * <p>Auth flow: bearer token is resolved in priority order: --token flag > STUDYDECK_TOKEN env var
 * > ~/.config/studydeck/config file.
 *
 * <p>OAuth2 Device Authorization Grant is the intended production auth flow. For development, use
 * STUDYDECK_TOKEN env var or --token flag.
 */
@Command(
    name = "studydeck",
    mixinStandardHelpOptions = true,
    version = "studydeck 0.1.0",
    description = "StudyDeck AI CLI — manage decks, notes, cards and study sessions.",
    subcommands = {
      AuthCommand.class,
      DeckCommand.class,
      NoteCommand.class,
      ImportCommand.class,
      ExportCommand.class,
      StudyCommand.class,
      CommandLine.HelpCommand.class
    })
public class StudyDeckCli implements Runnable {

  @Spec CommandSpec spec;

  @Override
  public void run() {
    spec.commandLine().usage(System.out);
  }

  public static void main(String[] args) {
    int exitCode = new CommandLine(new StudyDeckCli()).execute(args);
    System.exit(exitCode);
  }
}
