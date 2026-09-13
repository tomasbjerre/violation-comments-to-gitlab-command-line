package se.bjurr.violations.main;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import picocli.CommandLine.IParameterConsumer;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;

class ViolationsArgConverter implements IParameterConsumer {

  @Override
  public void consumeParameters(
      final Stack<String> args, final ArgSpec argSpec, final CommandSpec commandSpec) {
    if (args.size() < 4) {
      throw new ParameterException(
          commandSpec.commandLine(),
          "Specify violation with parameters: <PARSER> <FOLDER> <REGEXP PATTERN> <NAME>");
    }
    final List<String> violation = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      violation.add(args.pop());
    }
    @SuppressWarnings("unchecked")
    final List<List<String>> currentValue = (List<List<String>>) argSpec.getValue();
    currentValue.add(violation);
  }
}
