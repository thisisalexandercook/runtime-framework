package io.github.eisop.runtimeframework.core;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Method;
import java.util.Arrays;

public class AnnotationInstrumenter extends RuntimeInstrumenter {

  private final EnforcementPolicy policy;
  private final HierarchyResolver hierarchyResolver;

  public AnnotationInstrumenter(EnforcementPolicy policy, HierarchyResolver hierarchyResolver) {
    this.policy = policy;
    this.hierarchyResolver = hierarchyResolver;
  }

  @Override
  protected void generateParameterCheck(
      CodeBuilder b, int slotIndex, TypeKind type, MethodModel method, int paramIndex) {
    TargetAnnotation target = policy.getParameterCheck(method, paramIndex, type);
    if (target != null) {
      b.aload(slotIndex);
      target.check(b, type, "Parameter " + paramIndex);
    }
  }

  @Override
  protected void generateFieldWriteCheck(
      CodeBuilder b, FieldInstruction field, ClassModel classModel) {
    if (!field.owner().asInternalName().equals(classModel.thisClass().asInternalName())) return;

    FieldModel targetField = findField(classModel, field);
    if (targetField == null) return;

    TypeKind type = TypeKind.fromDescriptor(field.typeSymbol().descriptorString());
    TargetAnnotation target = policy.getFieldWriteCheck(targetField, type);

    if (target != null) {
      if (field.opcode() == Opcode.PUTSTATIC) {
        b.dup();
        target.check(b, type, "Static Field '" + field.name().stringValue() + "'");
      } else if (field.opcode() == Opcode.PUTFIELD) {
        b.dup_x1();
        target.check(b, type, "Field '" + field.name().stringValue() + "'");
        b.swap();
      }
    }
  }

  @Override
  protected void generateFieldReadCheck(
      CodeBuilder b, FieldInstruction field, ClassModel classModel) {
    TargetAnnotation target = null;
    TypeKind type = TypeKind.fromDescriptor(field.typeSymbol().descriptorString());

    if (field.owner().asInternalName().equals(classModel.thisClass().asInternalName())) {
      FieldModel targetField = findField(classModel, field);
      if (targetField != null) {
        target = policy.getFieldReadCheck(targetField, type);
      }
    } else {
      target =
          policy.getBoundaryFieldReadCheck(
              field.owner().asInternalName(), field.name().stringValue(), type);
    }

    if (target != null) {
      if (type.slotSize() == 1) {
        b.dup();
        target.check(b, type, "Read Field '" + field.name().stringValue() + "'");
      }
    }
  }

  @Override
  protected void generateReturnCheck(CodeBuilder b, ReturnInstruction ret, MethodModel method) {
    TargetAnnotation target = policy.getReturnCheck(method);
    if (target != null) {
      b.dup();
      target.check(b, TypeKind.REFERENCE, "Return value of " + method.methodName().stringValue());
    }
  }

  @Override
  protected void generateMethodCallCheck(CodeBuilder b, InvokeInstruction invoke) {
    TargetAnnotation target =
        policy.getBoundaryCallCheck(invoke.owner().asInternalName(), invoke.typeSymbol());
    if (target != null) {
      b.dup();
      target.check(
          b, TypeKind.REFERENCE, "Result from unchecked method " + invoke.name().stringValue());
    }
  }

  @Override
  protected void generateBridgeMethods(ClassBuilder builder, ClassModel model, ClassLoader loader) {
    for (Method parentMethod : hierarchyResolver.resolveUncheckedMethods(model, loader)) {
      if (policy.shouldGenerateBridge(parentMethod)) {
        emitBridge(builder, parentMethod);
      }
    }
  }

  private void emitBridge(ClassBuilder builder, Method parentMethod) {
    String methodName = parentMethod.getName();
    MethodTypeDesc desc =
        MethodTypeDesc.of(
            ClassDesc.ofDescriptor(parentMethod.getReturnType().descriptorString()),
            Arrays.stream(parentMethod.getParameterTypes())
                .map(c -> ClassDesc.ofDescriptor(c.descriptorString()))
                .toArray(ClassDesc[]::new));

    builder.withMethod(
        methodName,
        desc,
        java.lang.reflect.Modifier.PUBLIC,
        methodBuilder -> {
          methodBuilder.withCode(
              codeBuilder -> {
                int slotIndex = 1;
                Class<?>[] paramTypes = parentMethod.getParameterTypes();

                // 1. Checks
                for (int i = 0; i < paramTypes.length; i++) {
                  TypeKind type =
                      TypeKind.from(ClassDesc.ofDescriptor(paramTypes[i].descriptorString()));

                  TargetAnnotation target = policy.getBridgeParameterCheck(parentMethod, i);
                  if (target != null) {
                    codeBuilder.aload(slotIndex);
                    target.check(
                        codeBuilder, type, "Parameter " + i + " in inherited method " + methodName);
                  }
                  slotIndex += type.slotSize();
                }

                // 2. Super Call
                codeBuilder.aload(0);
                slotIndex = 1;
                for (Class<?> pType : paramTypes) {
                  TypeKind type = TypeKind.from(ClassDesc.ofDescriptor(pType.descriptorString()));
                  loadLocal(codeBuilder, type, slotIndex);
                  slotIndex += type.slotSize();
                }

                ClassDesc parentDesc = ClassDesc.of(parentMethod.getDeclaringClass().getName());
                codeBuilder.invokespecial(parentDesc, methodName, desc);

                // 3. Return
                returnResult(codeBuilder, parentMethod.getReturnType());
              });
        });
  }

  private FieldModel findField(ClassModel classModel, FieldInstruction field) {
    for (FieldModel fm : classModel.fields()) {
      if (fm.fieldName().stringValue().equals(field.name().stringValue())
          && fm.fieldType().stringValue().equals(field.type().stringValue())) {
        return fm;
      }
    }
    return null;
  }

  private void loadLocal(CodeBuilder b, TypeKind type, int slot) {
    switch (type) {
      case INT, BYTE, CHAR, SHORT, BOOLEAN -> b.iload(slot);
      case LONG -> b.lload(slot);
      case FLOAT -> b.fload(slot);
      case DOUBLE -> b.dload(slot);
      case REFERENCE -> b.aload(slot);
      default -> throw new IllegalArgumentException("Unknown type");
    }
  }

  private void returnResult(CodeBuilder b, Class<?> returnType) {
    if (returnType == void.class) b.return_();
    else if (returnType == int.class || returnType == boolean.class) b.ireturn();
    else if (returnType == long.class) b.lreturn();
    else if (returnType == float.class) b.freturn();
    else if (returnType == double.class) b.dreturn();
    else b.areturn();
  }
}
