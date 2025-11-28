package io.github.eisop.runtimeframework.core;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeAnnotation;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AnnotationInstrumenter extends RuntimeInstrumenter {

  private final Map<String, TargetAnnotation> targets;

  public AnnotationInstrumenter(Collection<TargetAnnotation> targetAnnotations) {
    this.targets =
        targetAnnotations.stream()
            .collect(Collectors.toMap(t -> t.annotationType().descriptorString(), t -> t));
  }

  @Override
  protected void generateParamCheck(
      CodeBuilder b, int slotIndex, TypeKind type, MethodModel method, int paramIndex) {
    List<java.lang.classfile.Annotation> paramAnnotations =
        getParameterAnnotations(method, paramIndex);
    for (java.lang.classfile.Annotation annotation : paramAnnotations) {
      String descriptor = annotation.classSymbol().descriptorString();
      TargetAnnotation target = targets.get(descriptor);
      if (target != null) {
        b.aload(slotIndex);
        target.check(b, type, "Parameter " + paramIndex);
      }
    }
  }

  private List<java.lang.classfile.Annotation> getParameterAnnotations(
      MethodModel method, int paramIndex) {
    List<java.lang.classfile.Annotation> result = new ArrayList<>();
    method
        .findAttribute(Attributes.runtimeVisibleParameterAnnotations())
        .ifPresent(
            attr -> {
              List<List<java.lang.classfile.Annotation>> allParams = attr.parameterAnnotations();
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

    FieldModel targetField = null;
    for (FieldModel fm : classModel.fields()) {
      if (fm.fieldName().equals(field.name()) && fm.fieldType().equals(field.type())) {
        targetField = fm;
        break;
      }
    }
    if (targetField == null) return;

    List<java.lang.classfile.Annotation> annotations = new ArrayList<>();
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

    for (java.lang.classfile.Annotation annotation : annotations) {
      TargetAnnotation target = targets.get(annotation.classSymbol().descriptorString());
      if (target != null) injectFieldCheck(b, field, target);
    }
  }

  private void injectFieldCheck(CodeBuilder b, FieldInstruction field, TargetAnnotation target) {
    TypeKind type = TypeKind.fromDescriptor(field.typeSymbol().descriptorString());
    if (type.slotSize() != 1) return;
    // TODO: support cat 2 types (long/double)
    if (field.opcode() == Opcode.PUTSTATIC) {
      b.dup();
      target.check(b, type, "Static Field '" + field.name().stringValue() + "'");
    } else if (field.opcode() == Opcode.PUTFIELD) {
      b.dup_x1();
      target.check(b, type, "Field '" + field.name().stringValue() + "'");
      b.swap();
    }
  }

  @Override
  protected void generateFieldReadCheck(CodeBuilder b, FieldInstruction field) {}

  @Override
  protected void generateReturnCheck(CodeBuilder b, ReturnInstruction ret, MethodModel method) {
    if (ret.opcode() != Opcode.ARETURN) return;

    List<java.lang.classfile.Annotation> returnAnnotations = new ArrayList<>();

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

    for (java.lang.classfile.Annotation annotation : returnAnnotations) {
      TargetAnnotation target = targets.get(annotation.classSymbol().descriptorString());

      if (target != null) {
        b.dup();
        target.check(b, TypeKind.REFERENCE, "Return value of " + method.methodName().stringValue());
      }
    }
  }
}
