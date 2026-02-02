package io.github.eisop.runtimeframework.core;

import io.github.eisop.runtimeframework.runtime.AttributionKind;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;

/**
 * A functional interface for generating runtime verification bytecode.
 *
 * <p>Implementations are responsible for emitting instructions to verify that a value on the
 * operand stack satisfies a specific property.
 */
@FunctionalInterface
public interface CheckGenerator {

  /**
   * Generates bytecode to verify a property.
   *
   * <p><b>Contract:</b> The value to be checked is already at the top of the operand stack. This
   * method must consume that value (e.g., by checking it) or restore the stack state (e.g., by
   * checking a duplicated value).
   *
   * @param b The CodeBuilder to emit instructions into.
   * @param type The type of the value on the stack.
   * @param diagnosticName A human-readable name for the value (e.g., "Parameter 0") to be used in
   *     error messages.
   */
  void generateCheck(CodeBuilder b, TypeKind type, String diagnosticName);

  /**
   * Returns a verifier that attributes the violation according to the given strategy.
   *
   * @param kind The attribution strategy.
   * @return A verifier with the specified attribution.
   */
  default CheckGenerator withAttribution(AttributionKind kind) {
    return this;
  }
}
