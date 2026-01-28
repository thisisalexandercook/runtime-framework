package io.github.eisop.runtimeframework.policy;

import io.github.eisop.runtimeframework.core.RuntimeVerifier;
import io.github.eisop.runtimeframework.resolution.ParentMethod;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeKind;
import java.lang.constant.MethodTypeDesc;

/** Defines the rules for WHEN to inject a runtime check. */
public interface EnforcementPolicy {

  /** Should we check this specific parameter at method entry? */
  RuntimeVerifier getParameterCheck(MethodModel method, int paramIndex, TypeKind type);

  /** Should we check a write to this field? */
  RuntimeVerifier getFieldWriteCheck(FieldModel field, TypeKind type);

  /** Should we check a read from this field? */
  RuntimeVerifier getFieldReadCheck(FieldModel field, TypeKind type);

  /** Should we check this return value? */
  RuntimeVerifier getReturnCheck(MethodModel method);

  /**
   * Should we check a write to a field in an EXTERNAL class? (Used when Unchecked code writes to
   * Checked code).
   */
  default RuntimeVerifier getBoundaryFieldWriteCheck(
      String owner, String fieldName, TypeKind type) {
    return null;
  }

  /** We are calling a method on 'owner'. Should we check the result? */
  RuntimeVerifier getBoundaryCallCheck(String owner, MethodTypeDesc desc);

  /** We are reading field from an EXTERNAL class. Should we check the value? */
  RuntimeVerifier getBoundaryFieldReadCheck(String owner, String fieldName, TypeKind type);

  /** Should we generate a bridge for this inherited method? */
  boolean shouldGenerateBridge(ParentMethod parentMethod);

  /** For a bridge we are generating, what check applies to this parameter? */
  RuntimeVerifier getBridgeParameterCheck(ParentMethod parentMethod, int paramIndex);

  /** Should we check an value being stored into an array? */
  RuntimeVerifier getArrayStoreCheck(TypeKind componentType);

  /** Should we check a value being read from an array? */
  RuntimeVerifier getArrayLoadCheck(TypeKind componentType);

  /** Should we check a value being stored in a variable? */
  RuntimeVerifier getLocalVariableWriteCheck(MethodModel method, int slot, TypeKind type);

  /** Should we check the return of an unchecked override? */
  default RuntimeVerifier getUncheckedOverrideReturnCheck(
      ClassModel classModel, MethodModel method, ClassLoader loader) {
    return null;
  }
}
