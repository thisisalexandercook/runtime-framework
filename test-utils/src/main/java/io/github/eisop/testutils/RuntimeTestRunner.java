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
        resourceDir = Path.of("checker/src/test/resources/" + resourcePath);
      }

      if (!Files.exists(resourceDir)) {
        throw new IOException("Test directory not found: " + resourceDir.toAbsolutePath());
      }

      List<Path> javaFiles;
      try (var stream = Files.walk(resourceDir)) {
        javaFiles = stream.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
      }

      if (javaFiles.isEmpty()) return;

      List<String> fileNames = new ArrayList<>();
      for (Path p : javaFiles) {
        String fname = p.getFileName().toString();
        Files.copy(p, tempDir.resolve(fname), StandardCopyOption.REPLACE_EXISTING);
        fileNames.add(fname);
      }

      compile(fileNames);

      List<Path> mainFiles = new ArrayList<>();
      List<Path> helperFiles = new ArrayList<>();

      for (Path sourcePath : javaFiles) {
        String content = Files.readString(sourcePath);
        if (content.contains("public static void main")) {
          mainFiles.add(sourcePath);
        } else {
          helperFiles.add(sourcePath);
        }
      }

      for (Path mainSource : mainFiles) {
        runSingleTest(mainSource, helperFiles, checkerClass, isGlobal);
      }

    } finally {
      cleanup();
    }
  }

  private void runSingleTest(
      Path mainSource, List<Path> helperFiles, String checkerClass, boolean isGlobal)
      throws Exception {
    System.out.println("Running test: " + mainSource.getFileName());

    List<ExpectedError> expectedErrors = new ArrayList<>();
    expectedErrors.addAll(parseExpectedErrors(mainSource));
    for (Path helper : helperFiles) {
      expectedErrors.addAll(parseExpectedErrors(helper));
    }

    String filename = mainSource.getFileName().toString();
    String mainClass = filename.replace(".java", "");

    TestResult result =
        runAgent(
            mainClass,
            isGlobal,
            "-Druntime.checker=" + checkerClass,
            "-Druntime.trustAnnotatedFor=true",
            "-Druntime.handler=io.github.eisop.testutils.TestViolationHandler");

    verifyErrors(expectedErrors, result.stdout(), filename);
  }

  private List<ExpectedError> parseExpectedErrors(Path sourceFile) throws IOException {
    String fileName = sourceFile.getFileName().toString();
    List<String> lines = Files.readAllLines(sourceFile);
    List<ExpectedError> errors = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      Matcher m = ERROR_PATTERN.matcher(lines.get(i));
      if (m.find()) {
        errors.add(new ExpectedError(fileName, i + 1, m.group(1).trim()));
      }
    }
    return errors;
  }

  @SuppressWarnings("StringSplitter")
  private void verifyErrors(List<ExpectedError> expected, String stdout, String testName) {
    List<ExpectedError> actualErrors = new ArrayList<>();

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
                    String errFile = locParts[0];
                    long lineNum = Long.parseLong(locParts[1]);

                    int msgStart = line.indexOf(") ") + 2;
                    String msg = (msgStart > 1) ? line.substring(msgStart) : "";

                    // FIX: Added 'true ||' logic implicitly by removing the filename check.
                    // Now we accept errors from ANY file involved in the test.
                    actualErrors.add(new ExpectedError(errFile, lineNum, msg.trim()));
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
            // 1. Must match Filename
            if (!exp.filename().equals(act.filename())) continue;

            // 2. Must match Message
            if (exp.expectedMessage().equals(act.expectedMessage())) {
              // 3. Fuzzy Line Check (+/- 5 lines)
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
      sb.append("\n=== TEST FAILED: ").append(testName).append(" ===\n");
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
