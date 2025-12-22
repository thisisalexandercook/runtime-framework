package io.github.eisop.checker.nullness;

import io.github.eisop.testutils.RuntimeTestRunner;
import org.junit.jupiter.api.Test;

public class NullnessDirectoryTest extends RuntimeTestRunner {

  @Test
  public void testParameterScenarios() throws Exception {
    runDirectoryTest(
        "nullness-parameter",
        "io.github.eisop.runtimeframework.checker.nullness.NullnessRuntimeChecker",
        false);
  }

  @Test
  public void testBoundaryScenarios() throws Exception {
    runDirectoryTest(
        "nullness-boundary",
        "io.github.eisop.runtimeframework.checker.nullness.NullnessRuntimeChecker",
        false);
  }

  @Test
  public void testFieldReadScenarios() throws Exception {
    runDirectoryTest(
        "nullness-field-read",
        "io.github.eisop.runtimeframework.checker.nullness.NullnessRuntimeChecker",
        false);
  }

  @Test
  public void testFieldWriteScenarios() throws Exception {
    runDirectoryTest(
        "nullness-field-write",
        "io.github.eisop.runtimeframework.checker.nullness.NullnessRuntimeChecker",
        true);
  }

  @Test
  public void testBridgeGeneration() throws Exception {
    runDirectoryTest(
        "nullness-bridge",
        "io.github.eisop.runtimeframework.checker.nullness.NullnessRuntimeChecker",
        false);
  }

  @Test
  public void testGlobalInheritance() throws Exception {
    runDirectoryTest(
        "nullness-global-bridge",
        "io.github.eisop.runtimeframework.checker.nullness.NullnessRuntimeChecker",
        true);
  }
}
