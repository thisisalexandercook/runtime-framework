package io.github.eisop.runtimeframework.planning;

import java.lang.classfile.MethodModel;
import java.util.Objects;

/** Per-method planning context derived during transformation. */
public record MethodContext(ClassContext classContext, MethodModel methodModel) {

  public MethodContext {
    Objects.requireNonNull(classContext, "classContext");
    Objects.requireNonNull(methodModel, "methodModel");
  }
}
