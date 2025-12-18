package io.github.eisop.checker.nullness;

import io.github.eisop.testutils.RuntimeTestRunner;
import org.junit.jupiter.api.Test;

public class NullnessDirectoryTest extends RuntimeTestRunner {

  @Test
  public void testBasicScenarios() throws Exception {
    // This looks for files in src/test/resources/test-cases/nullness-basic
    runDirectoryTest(
        "nullness-basic",
        "io.github.eisop.runtimeframework.checker.nullness.NullnessRuntimeChecker");
  }
}
