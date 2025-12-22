package io.github.eisop.runtimeframework.policy;

import io.github.eisop.runtimeframework.core.TargetAnnotation;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeKind;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Method;

/** Defines the rules for WHEN to inject a runtime check. */
public interface EnforcementPolicy {

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
   */
  default TargetAnnotation getBoundaryFieldWriteCheck(
      String owner, String fieldName, TypeKind type) {
    return null;
  }

  /** We are calling a method on 'owner'. Should we check the result? */
  TargetAnnotation getBoundaryCallCheck(String owner, MethodTypeDesc desc);

  /** We are reading field from an EXTERNAL class. Should we check the value? */
  TargetAnnotation getBoundaryFieldReadCheck(String owner, String fieldName, TypeKind type);

  /** Should we generate a bridge for this inherited method? */
  boolean shouldGenerateBridge(Method parentMethod);

  /** For a bridge we are generating, what check applies to this parameter? */
  TargetAnnotation getBridgeParameterCheck(Method parentMethod, int paramIndex);

  /** Should we check an value being stored into an array? */
  TargetAnnotation getArrayStoreCheck(TypeKind componentType);

  /** Should we check a value being read from an array? */
  TargetAnnotation getArrayLoadCheck(TypeKind componentType);

  /** Should we check a value being stored in a variable? */
  TargetAnnotation getLocalVariableWriteCheck(MethodModel method, int slot, TypeKind type);

  /** Should we check the return of an unchecked override? */
  default TargetAnnotation getUncheckedOverrideReturnCheck(
      ClassModel classModel, MethodModel method, ClassLoader loader) {
    return null;
  }
}
