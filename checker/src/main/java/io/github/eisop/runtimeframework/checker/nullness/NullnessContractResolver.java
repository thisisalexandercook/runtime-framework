package io.github.eisop.runtimeframework.checker.nullness;

import io.github.eisop.runtimeframework.contracts.PropertyId;
import io.github.eisop.runtimeframework.contracts.PropertyRequirement;
import io.github.eisop.runtimeframework.contracts.ValueContract;
import io.github.eisop.runtimeframework.planning.TargetRef;
import io.github.eisop.runtimeframework.resolution.ResolutionEnvironment;
import io.github.eisop.runtimeframework.semantics.ContractResolver;
import io.github.eisop.runtimeframework.semantics.ResolutionContext;
import java.lang.classfile.Annotation;
import java.lang.classfile.Attributes;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeAnnotation;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Resolves nullness contracts for runtime flow targets. */
public final class NullnessContractResolver implements ContractResolver {

  private static final String NON_NULL_DESC = NonNull.class.descriptorString();
  private static final String NULLABLE_DESC = Nullable.class.descriptorString();
  private static final ValueContract NON_NULL_CONTRACT =
      ValueContract.of(PropertyRequirement.of(PropertyId.NON_NULL));

  @Override
  public ValueContract resolve(TargetRef target, ResolutionContext context) {
    return switch (target) {
      case TargetRef.MethodParameter methodParameter -> resolveMethodParameter(methodParameter);
      case TargetRef.MethodReturn methodReturn -> resolveMethodReturn(methodReturn);
      case TargetRef.InvokedMethod invokedMethod -> resolveInvokedMethod(invokedMethod, context);
      case TargetRef.Field field -> resolveField(field, context);
      case TargetRef.ArrayComponent arrayComponent -> resolveArrayComponent(arrayComponent);
      case TargetRef.Local local -> resolveLocal(local, context);
      case TargetRef.Receiver receiver -> NON_NULL_CONTRACT;
    };
  }

  private ValueContract resolveMethodParameter(TargetRef.MethodParameter target) {
    MethodTypeDesc descriptor = target.method().methodTypeSymbol();
    if (!isReferenceDescriptor(
        descriptor.parameterList().get(target.parameterIndex()).descriptorString())) {
      return ValueContract.none();
    }
    return resolveAnnotations(
        getMethodParameterAnnotations(target.method(), target.parameterIndex()), true);
  }

  private ValueContract resolveMethodReturn(TargetRef.MethodReturn target) {
    if (!isReferenceDescriptor(target.method().methodTypeSymbol().returnType().descriptorString())) {
      return ValueContract.none();
    }
    return resolveAnnotations(getMethodReturnAnnotations(target.method()), true);
  }

  private ValueContract resolveInvokedMethod(
      TargetRef.InvokedMethod target, ResolutionContext context) {
    if (!isReferenceDescriptor(target.descriptor().returnType().descriptorString())) {
      return ValueContract.none();
    }

    return context
        .resolutionEnvironment()
        .findDeclaredMethod(
            target.ownerInternalName(),
            target.methodName(),
            target.descriptor().descriptorString(),
            context.loader())
        .map(method -> resolveAnnotations(getMethodReturnAnnotations(method), true))
        .orElse(NON_NULL_CONTRACT);
  }

  private ValueContract resolveField(TargetRef.Field target, ResolutionContext context) {
    boolean isReference = isReferenceDescriptor(target.descriptor());
    if (!isReference) {
      return ValueContract.none();
    }

    return context
        .resolutionEnvironment()
        .findDeclaredField(target.ownerInternalName(), target.fieldName(), context.loader())
        .map(field -> resolveAnnotations(getFieldAnnotations(field), true))
        .orElse(NON_NULL_CONTRACT);
  }

  private ValueContract resolveArrayComponent(TargetRef.ArrayComponent target) {
    return isReferenceArrayComponent(target.arrayDescriptor())
        ? NON_NULL_CONTRACT
        : ValueContract.none();
  }

  private ValueContract resolveLocal(TargetRef.Local target, ResolutionContext context) {
    List<Annotation> annotations = new ArrayList<>();
    ResolutionEnvironment resolutionEnvironment = context.resolutionEnvironment();
    for (ResolutionEnvironment.LocalVariableTypeAnnotation localAnnotation :
        resolutionEnvironment.getLocalVariableTypeAnnotations(target.method(), target.slot())) {
      annotations.add(localAnnotation.annotation());
    }
    return resolveAnnotations(annotations, true);
  }

  private ValueContract resolveAnnotations(List<Annotation> annotations, boolean isReference) {
    if (!isReference) {
      return ValueContract.none();
    }
    for (Annotation annotation : annotations) {
      String descriptor = annotation.classSymbol().descriptorString();
      if (NULLABLE_DESC.equals(descriptor)) {
        return ValueContract.none();
      }
      if (NON_NULL_DESC.equals(descriptor)) {
        return NON_NULL_CONTRACT;
      }
    }
    return NON_NULL_CONTRACT;
  }

  private List<Annotation> getMethodParameterAnnotations(MethodModel method, int parameterIndex) {
    List<Annotation> annotations = new ArrayList<>();
    method
        .findAttribute(Attributes.runtimeVisibleParameterAnnotations())
        .ifPresent(
            attr -> {
              List<List<Annotation>> all = attr.parameterAnnotations();
              if (parameterIndex < all.size()) {
                annotations.addAll(all.get(parameterIndex));
              }
            });
    method
        .findAttribute(Attributes.runtimeVisibleTypeAnnotations())
        .ifPresent(
            attr -> {
              for (TypeAnnotation annotation : attr.annotations()) {
                if (annotation.targetInfo() instanceof TypeAnnotation.FormalParameterTarget target
                    && target.formalParameterIndex() == parameterIndex) {
                  annotations.add(annotation.annotation());
                }
              }
            });
    return annotations;
  }

  private List<Annotation> getMethodReturnAnnotations(MethodModel method) {
    List<Annotation> annotations = new ArrayList<>();
    method
        .findAttribute(Attributes.runtimeVisibleAnnotations())
        .ifPresent(attr -> annotations.addAll(attr.annotations()));
    method
        .findAttribute(Attributes.runtimeVisibleTypeAnnotations())
        .ifPresent(
            attr -> {
              for (TypeAnnotation annotation : attr.annotations()) {
                if (annotation.targetInfo().targetType() == TypeAnnotation.TargetType.METHOD_RETURN) {
                  annotations.add(annotation.annotation());
                }
              }
            });
    return annotations;
  }

  private List<Annotation> getFieldAnnotations(FieldModel field) {
    List<Annotation> annotations = new ArrayList<>();
    field
        .findAttribute(Attributes.runtimeVisibleAnnotations())
        .ifPresent(attr -> annotations.addAll(attr.annotations()));
    field
        .findAttribute(Attributes.runtimeVisibleTypeAnnotations())
        .ifPresent(
            attr -> {
              for (TypeAnnotation annotation : attr.annotations()) {
                if (annotation.targetInfo().targetType() == TypeAnnotation.TargetType.FIELD) {
                  annotations.add(annotation.annotation());
                }
              }
            });
    return annotations;
  }

  private boolean isReferenceDescriptor(String descriptor) {
    return descriptor.startsWith("L") || descriptor.startsWith("[");
  }

  private boolean isReferenceArrayComponent(String arrayDescriptor) {
    return arrayDescriptor.startsWith("[L") || arrayDescriptor.startsWith("[[");
  }
}
