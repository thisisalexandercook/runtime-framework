package io.github.eisop.checker.nullness;

import io.github.eisop.testutils.RuntimeTestRunner;
import org.junit.jupiter.api.Test;

public class NullnessDirectoryTest extends RuntimeTestRunner {

  @Test
  public void testParameterScenarios() throws Exception {
    runDirectoryTest(
        "nullness-parameter",
        "io.github.eisop.runtimeframework.checker.nullness.NullnessRuntimeChecker");
  }

  @Test
  public void testBoundaryScenarios() throws Exception {
    runDirectoryTest(
        "nullness-boundary",
        "io.github.eisop.runtimeframework.checker.nullness.NullnessRuntimeChecker");
  }

  @Test
  public void testFieldScenarios() throws Exception {
    runDirectoryTest(
        "nullness-fields",
        "io.github.eisop.runtimeframework.checker.nullness.NullnessRuntimeChecker");
  }
}
