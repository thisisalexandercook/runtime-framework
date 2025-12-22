package io.github.eisop.runtimeframework.core;

import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.Filter;
import io.github.eisop.runtimeframework.policy.EnforcementPolicy;
import io.github.eisop.runtimeframework.resolution.HierarchyResolver;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Method;
import java.util.Arrays;

public class AnnotationInstrumenter extends RuntimeInstrumenter {

  private final EnforcementPolicy policy;
  private final HierarchyResolver hierarchyResolver;

  public AnnotationInstrumenter(
      EnforcementPolicy policy,
      HierarchyResolver hierarchyResolver,
      Filter<ClassInfo> safetyFilter) {
    super(safetyFilter);
    this.policy = policy;
    this.hierarchyResolver = hierarchyResolver;
  }

  @Override
  protected void generateArrayStoreCheck(CodeBuilder b, ArrayStoreInstruction instruction) {
    if (instruction.opcode() == Opcode.AASTORE) {
      TargetAnnotation target = policy.getArrayStoreCheck(TypeKind.REFERENCE);
      if (target != null) {
        b.dup();
        target.check(b, TypeKind.REFERENCE, "Array Element Write");
      }
    }
  }

  @Override
  protected void generateArrayLoadCheck(CodeBuilder b, ArrayLoadInstruction instruction) {
    if (instruction.opcode() == Opcode.AALOAD) {
      TargetAnnotation target = policy.getArrayLoadCheck(TypeKind.REFERENCE);
      if (target != null) {
        b.dup();
        target.check(b, TypeKind.REFERENCE, "Array Element Read");
      }
    }
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
    TargetAnnotation target = null;
    TypeKind type = TypeKind.fromDescriptor(field.typeSymbol().descriptorString());

    if (field.owner().asInternalName().equals(classModel.thisClass().asInternalName())) {
      FieldModel targetField = findField(classModel, field);
      if (targetField != null) {
        target = policy.getFieldWriteCheck(targetField, type);
      }
    } else {
      target =
          policy.getBoundaryFieldWriteCheck(
              field.owner().asInternalName(), field.name().stringValue(), type);
    }

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
  protected void generateUncheckedReturnCheck(
      CodeBuilder b,
      ReturnInstruction ret,
      MethodModel method,
      ClassModel classModel,
      ClassLoader loader) {
    if (ret.opcode() != Opcode.ARETURN) return;
    System.out.println(
        "[DEBUG] Checking Unchecked Return for "
            + classModel.thisClass().asInternalName()
            + "."
            + method.methodName().stringValue());

    TargetAnnotation target = policy.getUncheckedOverrideReturnCheck(classModel, method, loader);
    if (target != null) {
      System.out.println("[DEBUG] Policy returned target for override check.");

      b.dup();
      target.check(
          b,
          TypeKind.REFERENCE,
          "Return value of overridden method " + method.methodName().stringValue());
    }
    System.out.println("[DEBUG] Policy returned NULL for override check.");
  }

  @Override
  protected void generateMethodCallCheck(CodeBuilder b, InvokeInstruction invoke) {
    // empty for now, only need to generate checks when a method call is stored somehwhere

  }

  @Override
  protected void generateBridgeMethods(ClassBuilder builder, ClassModel model, ClassLoader loader) {
    for (Method parentMethod : hierarchyResolver.resolveUncheckedMethods(model, loader)) {
      if (policy.shouldGenerateBridge(parentMethod)) {
        emitBridge(builder, parentMethod);
      }
    }
  }

  @Override
  protected void generateStoreCheck(
      CodeBuilder b, StoreInstruction instruction, MethodModel method) {
    boolean isRefStore =
        switch (instruction.opcode()) {
          case ASTORE, ASTORE_0, ASTORE_1, ASTORE_2, ASTORE_3 -> true;
          default -> false;
        };

    if (!isRefStore) return;

    int slot = instruction.slot();
    TargetAnnotation target = policy.getLocalVariableWriteCheck(method, slot, TypeKind.REFERENCE);

    if (target != null) {
      b.dup();
      target.check(b, TypeKind.REFERENCE, "Local Variable Assignment (Slot " + slot + ")");
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

                codeBuilder.aload(0);
                slotIndex = 1;
                for (Class<?> pType : paramTypes) {
                  TypeKind type = TypeKind.from(ClassDesc.ofDescriptor(pType.descriptorString()));
                  loadLocal(codeBuilder, type, slotIndex);
                  slotIndex += type.slotSize();
                }

                ClassDesc parentDesc = ClassDesc.of(parentMethod.getDeclaringClass().getName());
                codeBuilder.invokespecial(parentDesc, methodName, desc);
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
