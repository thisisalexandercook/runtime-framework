package io.github.eisop.runtimeframework.policy;

import io.github.eisop.runtimeframework.core.TargetAnnotation;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeKind;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Method;

/**
 * Defines the rules for WHEN to inject a runtime check.
 *
 * <p>This separates the "Mechanism" (Bytecode Generation) from the "Policy" (Safety Rules). It
 * allows swapping between Strict, Lenient, or Debug policies without changing the instrumenter.
 */
public interface EnforcementPolicy {

  // --- 1. Internal Logic (Method Bodies) ---

  /** Should we check this specific parameter at method entry? */
  TargetAnnotation getParameterCheck(MethodModel method, int paramIndex, TypeKind type);

  /** Should we check a write to this field? */
  TargetAnnotation getFieldWriteCheck(FieldModel field, TypeKind type);

  /** Should we check a read from this field? */
  TargetAnnotation getFieldReadCheck(FieldModel field, TypeKind type);

  /** Should we check this return value? */
  TargetAnnotation getReturnCheck(MethodModel method);

  /**
   * Should we check a write to a field in an EXTERNAL class? (Used when Unchecked code writes to
   * Checked code).
   *
   * <p><b>Default:</b> Returns {@code null} (No check). Most policies do not instrument unchecked
   * code and therefore cannot enforce this.
   */
  default TargetAnnotation getBoundaryFieldWriteCheck(
      String owner, String fieldName, TypeKind type) {
    return null;
  }

  default TargetAnnotation getBoundaryMethodOverrideReturnCheck(String owner, MethodTypeDesc desc) {
    return null;
  }

  // --- 2. Boundary Logic (Calls & External Access) ---

  /** We are calling a method on 'owner'. Should we check the result? */
  TargetAnnotation getBoundaryCallCheck(String owner, MethodTypeDesc desc);

  /** We are reading a field from an EXTERNAL class. Should we check the value? */
  TargetAnnotation getBoundaryFieldReadCheck(String owner, String fieldName, TypeKind type);

  // --- 3. Inheritance Logic (Bridges) ---

  /** Should we generate a bridge for this inherited method? */
  boolean shouldGenerateBridge(Method parentMethod);

  /** For a bridge we are generating, what check applies to this parameter? */
  TargetAnnotation getBridgeParameterCheck(Method parentMethod, int paramIndex);

  /** Should we check an value being stored into an array? */
  TargetAnnotation getArrayStoreCheck(TypeKind componentType);

  /** Should we check a value being read from an array? */
  TargetAnnotation getArrayLoadCheck(TypeKind componentType);

  TargetAnnotation getLocalVariableWriteCheck(MethodModel method, int slot, TypeKind type);
}
