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

  private static final Pattern ERROR_PATTERN = Pattern.compile("//\\s*::\\s*error:\\s*\\((.+?)\\)");

  public void runDirectoryTest(String dirName, String checkerClass) throws Exception {
    setup();
    try {
      String resourcePath = "test-cases/" + dirName;
      Path resourceDir = Path.of("src/test/resources/" + resourcePath);

      if (!Files.exists(resourceDir)) {
        // Fallback for IDE vs Gradle working directory differences
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

    // Pass the Fully Qualified Name of the TestViolationHandler in test-utils
    TestResult result =
        runAgent(
            mainClass,
            "-Druntime.checker=" + checkerClass,
            "-Druntime.classes=" + mainClass,
            "-Druntime.handler=io.github.eisop.testutils.TestViolationHandler");

    verifyErrors(expectedErrors, result.stdout(), result.stderr(), filename);
  }

  private List<ExpectedError> parseExpectedErrors(Path sourceFile) throws IOException {
    List<String> lines = Files.readAllLines(sourceFile);
    List<ExpectedError> errors = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      Matcher m = ERROR_PATTERN.matcher(lines.get(i));
      if (m.find()) {
        // Line numbers are 1-based
        errors.add(new ExpectedError(i + 2, m.group(1).trim()));
      }
    }
    return errors;
  }

  @SuppressWarnings("StringSplitter") // We use simple split() to avoid Guava dependency
  private void verifyErrors(
      List<ExpectedError> expected, String stdout, String stderr, String filename) {
    List<ExpectedError> actualErrors = new ArrayList<>();

    // Parse STDOUT for [VIOLATION] lines
    // Use stream instead of split("\\R") to avoid StringSplitter warning and handle newlines
    // cleanly
    stdout
        .lines()
        .forEach(
            line -> {
              if (line.startsWith("[VIOLATION]")) {
                String[] parts = line.split(" ");
                if (parts.length > 1) {
                  String fileLoc = parts[1]; // "File.java:Line"
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

    // Check for matches allowing for a 1-line difference (comment on next line)
    boolean match = expected.size() == actualErrors.size();
    if (match) {
      for (int i = 0; i < expected.size(); i++) {
        ExpectedError exp = expected.get(i);
        ExpectedError act = actualErrors.get(i);

        // Fuzzy line match: Accept if actual line is same OR 1 line before the comment
        boolean lineMatch =
            (act.lineNumber() == exp.lineNumber()) || (act.lineNumber() == exp.lineNumber() - 1);

        if (!lineMatch || !exp.expectedMessage().equals(act.expectedMessage())) {
          match = false;
          break;
        }
      }
    }

    if (!match) {
      StringBuilder sb = new StringBuilder();
      sb.append("\n=== TEST FAILED: ").append(filename).append(" ===\n");
      sb.append("Expected Errors:\n");
      if (expected.isEmpty()) sb.append("  (None)\n");
      expected.forEach(e -> sb.append("  ").append(e).append("\n"));

      sb.append("Actual Violations:\n");
      if (actualErrors.isEmpty()) sb.append("  (None - Did the agent run?)\n");
      actualErrors.forEach(e -> sb.append("  ").append(e).append("\n"));

      sb.append("Full Stdout:\n").append(stdout).append("\n");
      sb.append("Full Stderr:\n").append(stderr).append("\n");
      sb.append("==========================================\n");

      // Print to stdout so it shows up in Gradle log even without --info
      System.out.println(sb.toString());

      Assertions.fail("Verification failed. See stdout for diff.");
    }
  }
}
