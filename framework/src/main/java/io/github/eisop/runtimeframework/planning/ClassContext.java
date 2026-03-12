package io.github.eisop.runtimeframework.planning;

import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.policy.ClassClassification;
import java.lang.classfile.ClassModel;
import java.util.Objects;

/** Per-class planning context derived during transformation. */
public record ClassContext(
    ClassInfo classInfo, ClassModel classModel, ClassClassification classification) {

  public ClassContext {
    Objects.requireNonNull(classInfo, "classInfo");
    Objects.requireNonNull(classModel, "classModel");
    Objects.requireNonNull(classification, "classification");
  }
}
