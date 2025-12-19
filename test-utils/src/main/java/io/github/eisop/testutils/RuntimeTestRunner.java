package io.github.eisop.testutils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;

public class RuntimeTestRunner extends AgentTestHarness {

  // Regex to find comments like: // :: error: (message)
  // Using greedy match .* to capture nested parens if necessary
  private static final Pattern ERROR_PATTERN = Pattern.compile("//\\s*::\\s*error:\\s*\\((.*)\\)");

  public void runDirectoryTest(String dirName, String checkerClass) throws Exception {
    setup();
    try {
      String resourcePath = "test-cases/" + dirName;
      Path resourceDir = Path.of("src/test/resources/" + resourcePath);

      if (!Files.exists(resourceDir)) {
        // Fallback for IDEs where working dir might be root
        resourceDir = Path.of("checker/src/test/resources/" + resourcePath);
      }

      if (!Files.exists(resourceDir)) {
        throw new IOException("Test directory not found: " + resourceDir.toAbsolutePath());
      }

      List<Path> javaFiles;
      try (var stream = Files.walk(resourceDir)) {
        javaFiles = stream.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
      }

      for (Path sourcePath : javaFiles) {
        runSingleTest(sourcePath, checkerClass);
      }
    } finally {
      cleanup();
    }
  }

  private void runSingleTest(Path sourcePath, String checkerClass) throws Exception {
    System.out.println("Running test: " + sourcePath.getFileName());
    List<ExpectedError> expectedErrors = parseExpectedErrors(sourcePath);

    String filename = sourcePath.getFileName().toString();
    Files.copy(sourcePath, tempDir.resolve(filename));
    compile(filename);

    String mainClass = filename.replace(".java", "");

    TestResult result =
        runAgent(
            mainClass,
            "-Druntime.checker=" + checkerClass,
            "-Druntime.classes=" + mainClass,
            "-Druntime.handler=io.github.eisop.testutils.TestViolationHandler");

    verifyErrors(expectedErrors, result.stdout(), filename);
  }

  private List<ExpectedError> parseExpectedErrors(Path sourceFile) throws IOException {
    List<String> lines = Files.readAllLines(sourceFile);
    List<ExpectedError> errors = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      Matcher m = ERROR_PATTERN.matcher(lines.get(i));
      if (m.find()) {
        // Line numbers are 1-based
        errors.add(new ExpectedError(i + 1, m.group(1).trim()));
      }
    }
    return errors;
  }

  @SuppressWarnings("StringSplitter")
  private void verifyErrors(List<ExpectedError> expected, String stdout, String filename) {
    List<ExpectedError> actualErrors = new ArrayList<>();

    // Parse STDOUT for [VIOLATION] lines
    stdout
        .lines()
        .forEach(
            line -> {
              if (line.startsWith("[VIOLATION]")) {
                // Format: [VIOLATION] File.java:Line (Checker) Message
                String[] parts = line.split(" ");
                if (parts.length > 1) {
                  String fileLoc = parts[1];
                  if (fileLoc.contains(":")) {
                    String[] locParts = fileLoc.split(":");
                    if (locParts[0].equals(filename)) {
                      long lineNum = Long.parseLong(locParts[1]);
                      int msgStart = line.indexOf(") ") + 2;
                      String msg = (msgStart > 1) ? line.substring(msgStart) : "";
                      actualErrors.add(new ExpectedError(lineNum, msg.trim()));
                    }
                  }
                }
              }
            });

    List<ExpectedError> unmatchedExpected = new ArrayList<>(expected);
    List<ExpectedError> unmatchedActual = new ArrayList<>(actualErrors);

    // Greedy matching
    unmatchedActual.removeIf(
        act -> {
          ExpectedError bestMatch = null;

          for (ExpectedError exp : unmatchedExpected) {
            if (exp.expectedMessage().equals(act.expectedMessage())) {
              // Fuzzy Line Check:
              // Runtime injection can squash parameter checks to the method start line.
              // Comments might be spread out over the parameter list.
              // Allow a tolerance of +/- 5 lines.
              long diff = Math.abs(act.lineNumber() - exp.lineNumber());
              if (diff <= 5) {
                bestMatch = exp;
                break; // Found it
              }
            }
          }

          if (bestMatch != null) {
            unmatchedExpected.remove(bestMatch);
            return true; // Match found
          }
          return false;
        });

    if (!unmatchedExpected.isEmpty() || !unmatchedActual.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      sb.append("\n=== TEST FAILED: ").append(filename).append(" ===\n");

      if (!unmatchedExpected.isEmpty()) {
        sb.append("Missing Expected Errors:\n");
        unmatchedExpected.forEach(e -> sb.append("  ").append(e).append("\n"));
      }

      if (!unmatchedActual.isEmpty()) {
        sb.append("Unexpected Runtime Violations:\n");
        unmatchedActual.forEach(e -> sb.append("  ").append(e).append("\n"));
      }

      sb.append("\nFull Stdout:\n").append(stdout).append("\n");
      System.out.println(sb.toString());

      Assertions.fail("Verification failed. Mismatched errors.");
    }
  }
}
