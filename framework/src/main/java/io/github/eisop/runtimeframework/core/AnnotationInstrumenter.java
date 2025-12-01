package io.github.eisop.runtimeframework.core;

import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.FrameworkSafetyFilter;
import java.lang.classfile.Annotation;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeAnnotation;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AnnotationInstrumenter extends RuntimeInstrumenter {

  private final Map<String, TargetAnnotation> targets;
  private final HierarchyResolver hierarchyResolver;

  public AnnotationInstrumenter(Collection<TargetAnnotation> targetAnnotations) {
    this.targets =
        targetAnnotations.stream()
            .collect(Collectors.toMap(t -> t.annotationType().descriptorString(), t -> t));
    FrameworkSafetyFilter safetyFilter = new FrameworkSafetyFilter();
    this.hierarchyResolver =
        new ReflectionHierarchyResolver(
            className -> safetyFilter.test(new ClassInfo(className.replace('.', '/'), null, null)));
  }

  @Override
  protected void generateParameterCheck(
      CodeBuilder b, int slotIndex, TypeKind type, MethodModel method, int paramIndex) {
    List<Annotation> paramAnnotations = getParameterAnnotations(method, paramIndex);
    for (Annotation annotation : paramAnnotations) {
      TargetAnnotation target = targets.get(annotation.classSymbol().descriptorString());
      if (target != null) {
        // only handle reference types for now
        b.aload(slotIndex);
        target.check(b, type, "Parameter " + paramIndex);
      }
    }
  }

  private List<Annotation> getParameterAnnotations(MethodModel method, int paramIndex) {
    List<Annotation> result = new ArrayList<>();
    method
        .findAttribute(Attributes.runtimeVisibleParameterAnnotations())
        .ifPresent(
            attr -> {
              List<List<Annotation>> allParams = attr.parameterAnnotations();
              if (paramIndex < allParams.size()) {
                result.addAll(allParams.get(paramIndex));
              }
            });
    method
        .findAttribute(Attributes.runtimeVisibleTypeAnnotations())
        .ifPresent(
            attr -> {
              for (TypeAnnotation typeAnno : attr.annotations()) {
                TypeAnnotation.TargetInfo target = typeAnno.targetInfo();
                if (target instanceof TypeAnnotation.FormalParameterTarget paramTarget) {
                  if (paramTarget.formalParameterIndex() == paramIndex) {
                    result.add(typeAnno.annotation());
                  }
                }
              }
            });
    return result;
  }

  @Override
  protected void generateFieldWriteCheck(
      CodeBuilder b, FieldInstruction field, ClassModel classModel) {
    if (!field.owner().equals(classModel.thisClass())) return;

    FieldModel targetField = findField(classModel, field);
    if (targetField == null) return;

    List<Annotation> annotations = getFieldAnnotations(targetField);

    for (Annotation annotation : annotations) {
      TargetAnnotation target = targets.get(annotation.classSymbol().descriptorString());
      if (target != null) {
        TypeKind type = TypeKind.fromDescriptor(field.typeSymbol().descriptorString());
        if (type.slotSize() != 1) return;

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
  }

  @Override
  protected void generateFieldReadCheck(
      CodeBuilder b, FieldInstruction field, ClassModel classModel) {
    // TODO: out of class fields
    if (!field.owner().equals(classModel.thisClass())) return;

    FieldModel targetField = findField(classModel, field);
    if (targetField == null) return;

    List<Annotation> annotations = getFieldAnnotations(targetField);

    for (Annotation annotation : annotations) {
      TargetAnnotation target = targets.get(annotation.classSymbol().descriptorString());
      if (target != null) {

        TypeKind type = TypeKind.fromDescriptor(field.typeSymbol().descriptorString());
        if (type.slotSize() == 1) {
          b.dup();
          target.check(b, type, "Read Field '" + field.name().stringValue() + "'");
        }
      }
    }
  }

  private FieldModel findField(ClassModel classModel, FieldInstruction field) {
    for (FieldModel fm : classModel.fields()) {
      if (fm.fieldName().equals(field.name()) && fm.fieldType().equals(field.type())) {
        return fm;
      }
    }
    return null;
  }

  private List<Annotation> getFieldAnnotations(FieldModel targetField) {
    List<Annotation> annotations = new ArrayList<>();
    targetField
        .findAttribute(Attributes.runtimeVisibleAnnotations())
        .ifPresent(attr -> annotations.addAll(attr.annotations()));
    targetField
        .findAttribute(Attributes.runtimeVisibleTypeAnnotations())
        .ifPresent(
            attr -> {
              for (TypeAnnotation ta : attr.annotations()) {
                if (ta.targetInfo() instanceof TypeAnnotation.EmptyTarget) {
                  annotations.add(ta.annotation());
                }
              }
            });
    return annotations;
  }

  @Override
  protected void generateReturnCheck(CodeBuilder b, ReturnInstruction ret, MethodModel method) {
    if (ret.opcode() != Opcode.ARETURN) return;
    List<Annotation> returnAnnotations = new ArrayList<>();
    method
        .findAttribute(Attributes.runtimeVisibleTypeAnnotations())
        .ifPresent(
            attr -> {
              for (TypeAnnotation typeAnno : attr.annotations()) {
                if (typeAnno.targetInfo().targetType() == TypeAnnotation.TargetType.METHOD_RETURN) {
                  returnAnnotations.add(typeAnno.annotation());
                }
              }
            });
    for (Annotation annotation : returnAnnotations) {
      TargetAnnotation target = targets.get(annotation.classSymbol().descriptorString());
      if (target != null) {
        b.dup();
        target.check(b, TypeKind.REFERENCE, "Return value of " + method.methodName().stringValue());
      }
    }
  }

  // --- Bridge Method Generation ---

  @Override
  protected void generateBridgeMethods(ClassBuilder builder, ClassModel model, ClassLoader loader) {
    for (Method parentMethod : hierarchyResolver.resolveUncheckedMethods(model, loader)) {
      boolean needsBridge = false;

      for (java.lang.annotation.Annotation[] paramAnnos : parentMethod.getParameterAnnotations()) {
        for (java.lang.annotation.Annotation anno : paramAnnos) {
          String desc = "L" + anno.annotationType().getName().replace('.', '/') + ";";
          if (targets.containsKey(desc)) {
            needsBridge = true;
            break;
          }
        }
      }

      if (needsBridge) {
        emitBridge(builder, parentMethod);
      }
    }
  }

  private void emitBridge(ClassBuilder builder, Method parentMethod) {
    String methodName = parentMethod.getName();

    // FIX: Use JDK APIs instead of ASM
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
                java.lang.annotation.Annotation[][] allAnnos =
                    parentMethod.getParameterAnnotations();
                Class<?>[] paramTypes = parentMethod.getParameterTypes();

                // 1. INJECT CHECKS
                for (int i = 0; i < paramTypes.length; i++) {
                  TypeKind type =
                      TypeKind.from(ClassDesc.ofDescriptor(paramTypes[i].descriptorString()));

                  for (java.lang.annotation.Annotation anno : allAnnos[i]) {
                    String annoDesc = "L" + anno.annotationType().getName().replace('.', '/') + ";";
                    TargetAnnotation target = targets.get(annoDesc);
                    if (target != null) {
                      codeBuilder.aload(slotIndex);
                      target.check(
                          codeBuilder,
                          type,
                          "Parameter " + i + " in inherited method " + methodName);
                    }
                  }
                  slotIndex += type.slotSize();
                }

                // 2. CALL SUPER
                codeBuilder.aload(0);
                slotIndex = 1;
                for (Class<?> pType : paramTypes) {
                  TypeKind type = TypeKind.from(ClassDesc.ofDescriptor(pType.descriptorString()));
                  loadLocal(codeBuilder, type, slotIndex);
                  slotIndex += type.slotSize();
                }

                ClassDesc parentDesc = ClassDesc.of(parentMethod.getDeclaringClass().getName());
                codeBuilder.invokespecial(parentDesc, methodName, desc);

                // 3. RETURN
                returnResult(codeBuilder, parentMethod.getReturnType());
              });
        });
  }

  private void loadLocal(CodeBuilder b, TypeKind type, int slot) {
    switch (type) {
      case INT, BYTE, CHAR, SHORT, BOOLEAN -> b.iload(slot);
      case LONG -> b.lload(slot);
      case FLOAT -> b.fload(slot);
      case DOUBLE -> b.dload(slot);
      case REFERENCE -> b.aload(slot);
      default -> throw new IllegalArgumentException("Unknown type: " + type);
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
