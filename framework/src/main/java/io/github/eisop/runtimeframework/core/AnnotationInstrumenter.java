package io.github.eisop.runtimeframework.core;

import java.lang.classfile.Attributes;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeAnnotation; // Needed for TypeAnnotation casting
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.util.ArrayList; // Needed for aggregation
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A reusable instrumenter that parses bytecode attributes to find annotations and delegates the
 * verification logic to registered {@link TargetAnnotation} strategies.
 */
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
    // 1. Find Annotations (Now scans both Declaration and Type attributes)
    List<java.lang.classfile.Annotation> paramAnnotations =
        getParameterAnnotations(method, paramIndex);

    // 2. Dispatch
    for (java.lang.classfile.Annotation annotation : paramAnnotations) {
      String descriptor = annotation.classSymbol().descriptorString();
      TargetAnnotation target = targets.get(descriptor);

      if (target != null) {
        // PLUMBING: Load the value onto the stack
        b.aload(slotIndex);

        // LOGIC: Delegate to the target
        target.check(b, type, "Parameter " + paramIndex);
      }
    }
  }

  private List<java.lang.classfile.Annotation> getParameterAnnotations(
      MethodModel method, int paramIndex) {
    List<java.lang.classfile.Annotation> result = new ArrayList<>();

    // 1. Check RuntimeVisibleParameterAnnotations (Legacy/Declaration annotations)
    method
        .findAttribute(Attributes.runtimeVisibleParameterAnnotations())
        .ifPresent(
            attr -> {
              List<List<java.lang.classfile.Annotation>> allParams = attr.parameterAnnotations();
              if (paramIndex < allParams.size()) {
                result.addAll(allParams.get(paramIndex));
              }
            });

    // 2. Check RuntimeVisibleTypeAnnotations (Modern Type Use annotations like @NonNull)
    method
        .findAttribute(Attributes.runtimeVisibleTypeAnnotations())
        .ifPresent(
            attr -> {
              for (TypeAnnotation typeAnno : attr.annotations()) {
                // We must check if this annotation targets the specific parameter index we are
                // visiting
                TypeAnnotation.TargetInfo target = typeAnno.targetInfo();

                if (target instanceof TypeAnnotation.FormalParameterTarget paramTarget) {
                  if (paramTarget.formalParameterIndex() == paramIndex) {
                    // TypeAnnotation wraps the actual Annotation in JDK 25
                    result.add(typeAnno.annotation());
                  }
                }
              }
            });

    return result;
  }

  @Override
  protected void generateFieldWriteCheck(CodeBuilder b, FieldInstruction field) {}

  @Override
  protected void generateFieldReadCheck(CodeBuilder b, FieldInstruction field) {}

  @Override
  protected void generateReturnCheck(CodeBuilder b, ReturnInstruction ret) {}
}
