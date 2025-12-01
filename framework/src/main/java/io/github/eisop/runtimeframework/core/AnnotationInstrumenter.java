package io.github.eisop.runtimeframework.core;

import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.Filter;
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
import java.lang.classfile.instruction.InvokeInstruction;
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
  private final Filter<ClassInfo> safetyFilter;
  private final TargetAnnotation defaultTarget;

  // UPDATED CONSTRUCTOR: Now accepts the Filter directly from the Checker
  public AnnotationInstrumenter(
      Collection<TargetAnnotation> targetAnnotations, Filter<ClassInfo> safetyFilter) {
    this.targets =
        targetAnnotations.stream()
            .collect(Collectors.toMap(t -> t.annotationType().descriptorString(), t -> t));

    this.defaultTarget = targetAnnotations.stream().findFirst().orElse(null);

    // Store the passed filter instead of recreating it from system properties
    this.safetyFilter = safetyFilter;

    // Use the passed filter for the hierarchy resolver too
    this.hierarchyResolver =
        new ReflectionHierarchyResolver(
            className ->
                this.safetyFilter.test(new ClassInfo(className.replace('.', '/'), null, null)));
  }

  @Override
  protected void generateParameterCheck(
      CodeBuilder b, int slotIndex, TypeKind type, MethodModel method, int paramIndex) {
    List<Annotation> paramAnnotations = getParameterAnnotations(method, paramIndex);
    for (Annotation annotation : paramAnnotations) {
      TargetAnnotation target = targets.get(annotation.classSymbol().descriptorString());
      if (target != null) {
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
                    // Assuming TypeAnnotation in recent JDK 25 builds exposes .annotation()
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

  @Override
  protected void generateMethodCallCheck(CodeBuilder b, InvokeInstruction invoke) {
    String ownerClass = invoke.owner().asInternalName();
    boolean isUncheckedTarget = !safetyFilter.test(new ClassInfo(ownerClass, null, null));

    if (isUncheckedTarget) {
      ClassDesc returnType = invoke.typeSymbol().returnType();
      TypeKind type = TypeKind.from(returnType);

      if (type == TypeKind.REFERENCE && defaultTarget != null) {
        b.dup();
        defaultTarget.check(b, type, "Result from unchecked method " + invoke.name().stringValue());
      }
    }
  }

  @Override
  protected void generateBridgeMethods(ClassBuilder builder, ClassModel model, ClassLoader loader) {
    for (Method parentMethod : hierarchyResolver.resolveUncheckedMethods(model, loader)) {

      // 1. DETERMINE IF BRIDGE IS NEEDED
      //    We check both Explicit Annotations AND the Implicit Default Policy
      boolean needsBridge = false;
      Class<?>[] paramTypes = parentMethod.getParameterTypes();
      java.lang.annotation.Annotation[][] paramAnnos = parentMethod.getParameterAnnotations();

      for (int i = 0; i < paramTypes.length; i++) {
        boolean explicitFound = false;

        // Check Explicit
        for (java.lang.annotation.Annotation anno : paramAnnos[i]) {
          String desc = "L" + anno.annotationType().getName().replace('.', '/') + ";";
          if (targets.containsKey(desc)) {
            needsBridge = true;
            explicitFound = true;
            break;
          }
        }

        // Check Default (If no explicit annotation and it's a reference type)
        if (!explicitFound && !paramTypes[i].isPrimitive() && defaultTarget != null) {
          needsBridge = true;
        }

        if (needsBridge) break;
      }

      if (needsBridge) {
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
                java.lang.annotation.Annotation[][] allAnnos =
                    parentMethod.getParameterAnnotations();
                Class<?>[] paramTypes = parentMethod.getParameterTypes();

                // 1. INJECT CHECKS
                for (int i = 0; i < paramTypes.length; i++) {
                  TypeKind type =
                      TypeKind.from(ClassDesc.ofDescriptor(paramTypes[i].descriptorString()));

                  boolean checkGenerated = false;

                  // A. Check Explicit Annotations
                  for (java.lang.annotation.Annotation anno : allAnnos[i]) {
                    String annoDesc = "L" + anno.annotationType().getName().replace('.', '/') + ";";
                    TargetAnnotation target = targets.get(annoDesc);
                    if (target != null) {
                      codeBuilder.aload(slotIndex);
                      target.check(
                          codeBuilder,
                          type,
                          "Parameter " + i + " in inherited method " + methodName);
                      checkGenerated = true;
                    }
                  }

                  // B. Check Default (Strict Mode for Unchecked Inheritance)
                  if (!checkGenerated && type == TypeKind.REFERENCE && defaultTarget != null) {
                    codeBuilder.aload(slotIndex);
                    defaultTarget.check(
                        codeBuilder,
                        type,
                        "Unannotated Parameter " + i + " in inherited method " + methodName);
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
