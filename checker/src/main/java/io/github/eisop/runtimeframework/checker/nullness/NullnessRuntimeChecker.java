package io.github.eisop.runtimeframework.checker.nullness;

import io.github.eisop.runtimeframework.core.RuntimeChecker;
import io.github.eisop.runtimeframework.semantics.CheckerSemantics;

public class NullnessRuntimeChecker extends RuntimeChecker {

  private static final CheckerSemantics SEMANTICS = new NullnessSemantics();

  @Override
  public String getName() {
    return "nullness";
  }

  @Override
  public CheckerSemantics getSemantics() {
    return SEMANTICS;
  }
}
