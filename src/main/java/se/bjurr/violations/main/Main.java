package se.bjurr.violations.main;

import picocli.CommandLine;

public class Main {

  public static void main(final String[] args) throws Exception {
    final CommandLine commandLine = new CommandLine(new Runner());
    commandLine.setExecutionExceptionHandler(new PrintExceptionMessageHandler());
    System.exit(commandLine.execute(args));
  }
}
