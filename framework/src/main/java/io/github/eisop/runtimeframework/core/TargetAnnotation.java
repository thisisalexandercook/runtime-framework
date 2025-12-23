package io.github.eisop.runtimeframework.core;

import java.lang.annotation.Annotation;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;

/** Represents a strategy for enforcing a property triggered by a specific annotation. */
public interface TargetAnnotation {

  /** The annotation class that triggers this check. */
  Class<? extends Annotation> annotationType();

  /**
   * Generates bytecode to verify the property.
   *
   * <p><b>Contract:</b> The value to be checked is already at the top of the operand stack. This
   * method must consume that value (e.g., by checking it) or restore the stack state.
   *
   * @param b The CodeBuilder to emit instructions into.
   * @param type The type of the value on the stack.
   * @param diagnosticName A human-readable name for the value (e.g., "Parameter 0", "Field 'x'") to
   *     be used in error messages.
   */
  void check(CodeBuilder b, TypeKind type, String diagnosticName);
}
