package io.github.eisop.runtimeframework.semantics;

import java.lang.classfile.TypeAnnotation;
import java.util.List;
import java.util.Objects;

/** A checker-relevant qualifier applied at a particular type-use path. */
public record TypeUseQualifier(
    String descriptor,
    List<TypeAnnotation.TypePathComponent> targetPath,
    boolean defaulted) {

  public TypeUseQualifier {
    Objects.requireNonNull(descriptor, "descriptor");
    targetPath = List.copyOf(Objects.requireNonNull(targetPath, "targetPath"));
  }

  public boolean isRoot() {
    return targetPath.isEmpty();
  }
}
