package io.github.eisop.testutils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public abstract class AgentTestHarness {

  protected Path tempDir;
  protected Path distDir;

  protected void setup() throws IOException {
    // Create a temp directory for this test run
    this.tempDir = Files.createTempDirectory("eisop-agent-test");

    // Locate the dist directory passed from Gradle
    String distPath = System.getProperty("agent.dist.dir");
    if (distPath == null) {
      // Fallback for running in IDE without Gradle context, assuming standard build layout
      Path potentialDist =
          Path.of(System.getProperty("user.dir")).resolve("../build/dist").normalize();
      if (Files.exists(potentialDist)) {
        distPath = potentialDist.toString();
      } else {
        throw new IllegalStateException(
            "System property 'agent.dist.dir' not set. Run via Gradle or set property.");
      }
    }
    this.distDir = Path.of(distPath);
  }

  protected void cleanup() throws IOException {
    // Recursive delete
    try (Stream<Path> walk = Files.walk(tempDir)) {
      walk.sorted((a, b) -> b.compareTo(a)) // Delete leaves first
          .forEach(
              p -> {
                try {
                  Files.delete(p);
                } catch (IOException e) {
                }
              });
    }
  }

  // --- Helper to copy test resources ---

  /**
   * Copies a test source file from src/test/resources/test-cases/ to the temp directory.
   *
   * @param resourcePath relative path in test-cases (e.g. "dispatch/Runner.java")
   */
  protected void copyTestFile(String resourcePath) throws IOException {
    // Load from classpath resources
    String fullPath = "test-cases/" + resourcePath;
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(fullPath)) {
      if (is == null) {
        // Try looking in file system directly (useful if resources aren't copied yet by IDE)
        Path fsPath = Path.of("src/test/resources/" + fullPath);
        if (Files.exists(fsPath)) {
          copyFileFromDisk(fsPath, resourcePath);
          return;
        }
        throw new IOException("Test resource not found: " + fullPath);
      }

      Path dest = tempDir.resolve(resourcePath);
      Files.createDirectories(dest.getParent());
      Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private void copyFileFromDisk(Path source, String relativeDest) throws IOException {
    Path dest = tempDir.resolve(relativeDest);
    Files.createDirectories(dest.getParent());
    Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
  }

  // Keep this for simple ad-hoc tests if needed
  protected void writeSource(String filename, String content) throws IOException {
    Path file = tempDir.resolve(filename);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content, StandardOpenOption.CREATE);
  }

  // --- Compilation ---

  protected void compile(List<String> filenames) throws Exception {
    compile(filenames.toArray(String[]::new));
  }

  protected void compile(String... filenames) throws Exception {
    compileWithClasspath(null, filenames);
  }

  protected void compileWithClasspath(String extraClasspath, String... filenames) throws Exception {
    // Find checker-qual jar
    Path qualJar = findJar("checker-qual");
    String cp = qualJar.toAbsolutePath().toString();
    if (extraClasspath != null) {
      cp += ":" + extraClasspath;
    }

    List<String> cmd = new ArrayList<>();
    cmd.add("javac");
    cmd.add("-cp");
    cmd.add(cp);
    cmd.add("-d");
    cmd.add(tempDir.toAbsolutePath().toString());

    for (String f : filenames) {
      cmd.add(tempDir.resolve(f).toAbsolutePath().toString());
    }

    runProcess(cmd, "Compilation");
  }

  // --- Execution ---

  protected TestResult runAgent(String mainClass, String... agentArgs) throws Exception {
    return runAgent(mainClass, false, agentArgs);
  }

  protected TestResult runAgent(String mainClass, boolean isGlobal, String... agentArgs)
      throws Exception {
    Path frameworkJar = findJar("framework");
    Path checkerJar = findJar("checker");
    Path qualJar = findJar("checker-qual");
    // We likely need test-utils jar on the classpath for the TestViolationHandler
    Path testUtilsJar = findJar("test-utils");

    // Build Classpath (Agent Jars + Test Classes + Test Utils)
    String cp =
        "."
            + ":"
            + frameworkJar.toAbsolutePath()
            + ":"
            + checkerJar.toAbsolutePath()
            + ":"
            + qualJar.toAbsolutePath()
            + ":"
            + testUtilsJar.toAbsolutePath();

    List<String> cmd = new ArrayList<>();
    cmd.add("java");
    cmd.add("--enable-preview");
    cmd.add("-javaagent:" + frameworkJar.toAbsolutePath());

    if (isGlobal) {
      cmd.add("-Druntime.global=true");
    }

    // Add agent arguments (e.g. -Druntime.classes=...)
    cmd.addAll(List.of(agentArgs));

    cmd.add("-cp");
    cmd.add(cp);
    cmd.add(mainClass);

    return runProcess(cmd, "Agent Execution");
  }

  private Path findJar(String prefix) throws IOException {
    try (Stream<Path> files = Files.list(distDir)) {
      return files
          .filter(
              p -> p.getFileName().toString().startsWith(prefix) && p.toString().endsWith(".jar"))
          .findFirst()
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "Could not find jar starting with " + prefix + " in " + distDir));
    }
  }

  private TestResult runProcess(List<String> cmd, String taskName) throws Exception {
    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.directory(tempDir.toFile());

    Process p = pb.start();

    // Read output
    String stdout = new String(p.getInputStream().readAllBytes());
    String stderr = new String(p.getErrorStream().readAllBytes());

    boolean finished = p.waitFor(10, TimeUnit.SECONDS);
    if (!finished) {
      p.destroy();
      throw new RuntimeException(taskName + " timed out.");
    }

    if (p.exitValue() != 0 && taskName.equals("Compilation")) {
      throw new RuntimeException("Compilation Failed:\n" + stderr);
    }

    return new TestResult(p.exitValue(), stdout, stderr);
  }

  protected record TestResult(int exitCode, String stdout, String stderr) {}
}
