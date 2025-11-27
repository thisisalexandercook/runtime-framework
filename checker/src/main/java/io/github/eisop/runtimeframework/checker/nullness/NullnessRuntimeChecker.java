package io.github.eisop.runtimeframework.checker.nullness;

import io.github.eisop.runtimeframework.core.AnnotationInstrumenter;
import io.github.eisop.runtimeframework.core.RuntimeChecker;
import io.github.eisop.runtimeframework.core.RuntimeInstrumenter;
import java.util.List;

public class NullnessRuntimeChecker extends RuntimeChecker {

  @Override
  public String getName() {
    return "Nullness Runtime Checker";
  }

  @Override
  public RuntimeInstrumenter getInstrumenter() {
    // Register our strategies
    return new AnnotationInstrumenter(List.of(new NonNullTarget()));
  }
}
