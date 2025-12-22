package io.github.eisop.testutils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;

public class RuntimeTestRunner extends AgentTestHarness {

  private static final Pattern ERROR_PATTERN = Pattern.compile("//\\s*::\\s*error:\\s*\\((.*)\\)");

  public void runDirectoryTest(String dirName, String checkerClass, boolean isGlobal)
      throws Exception {
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

      // 1. Gather all Java files
      List<Path> javaFiles;
      try (var stream = Files.walk(resourceDir)) {
        javaFiles = stream.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
      }

      if (javaFiles.isEmpty()) return;

      // 2. Copy all files to temp dir
      List<String> fileNames = new ArrayList<>();
      for (Path p : javaFiles) {
        String fname = p.getFileName().toString();
        Files.copy(p, tempDir.resolve(fname), StandardCopyOption.REPLACE_EXISTING);
        fileNames.add(fname);
      }

      // 3. Compile ALL files
      compile(fileNames);

      // 4. Run each file that has a main method
      for (Path sourcePath : javaFiles) {
        String content = Files.readString(sourcePath);
        if (content.contains("public static void main")) {
          runSingleTest(sourcePath, checkerClass, isGlobal);
        }
      }

    } finally {
      cleanup();
    }
  }

  private void runSingleTest(Path sourcePath, String checkerClass, boolean isGlobal)
      throws Exception {
    System.out.println("Running test: " + sourcePath.getFileName());
    List<ExpectedError> expectedErrors = parseExpectedErrors(sourcePath);

    String filename = sourcePath.getFileName().toString();
    String mainClass = filename.replace(".java", "");

    // We define the main class as the "Checked Class" for this test run.
    // Any other classes needed to be Checked must be marked with @AnnotatedFor
    // and we enable trustAnnotatedFor to pick them up.
    TestResult result =
        runAgent(
            mainClass,
            isGlobal,
            "-Druntime.checker=" + checkerClass,
            "-Druntime.classes=" + "test",
            "-Druntime.trustAnnotatedFor=true", // Enable auto-discovery for dependencies
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

    stdout
        .lines()
        .forEach(
            line -> {
              if (line.startsWith("[VIOLATION]")) {
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

    unmatchedActual.removeIf(
        act -> {
          ExpectedError bestMatch = null;
          for (ExpectedError exp : unmatchedExpected) {
            if (exp.expectedMessage().equals(act.expectedMessage())) {
              long diff = Math.abs(act.lineNumber() - exp.lineNumber());
              if (diff <= 5) {
                bestMatch = exp;
                break;
              }
            }
          }
          if (bestMatch != null) {
            unmatchedExpected.remove(bestMatch);
            return true;
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
      sb.append("\nFull Output:\n").append(stdout).append("\n");
      System.out.println(sb.toString());
      Assertions.fail("Verification failed. Mismatched errors.");
    }
  }
}
