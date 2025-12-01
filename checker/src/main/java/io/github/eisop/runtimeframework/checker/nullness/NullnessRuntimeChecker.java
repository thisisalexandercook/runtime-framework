package io.github.eisop.runtimeframework.checker.nullness;

import io.github.eisop.runtimeframework.core.AnnotationInstrumenter;
import io.github.eisop.runtimeframework.core.RuntimeChecker;
import io.github.eisop.runtimeframework.core.RuntimeInstrumenter;
import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.Filter;
import java.util.List;

public class NullnessRuntimeChecker extends RuntimeChecker {

  @Override
  public String getName() {
    return "Nullness Runtime Checker";
  }

  @Override
  public RuntimeInstrumenter getInstrumenter(Filter<ClassInfo> filter) {
    // Pass the filter to the instrumenter
    return new AnnotationInstrumenter(List.of(new NonNullTarget()), filter);
  }
}
