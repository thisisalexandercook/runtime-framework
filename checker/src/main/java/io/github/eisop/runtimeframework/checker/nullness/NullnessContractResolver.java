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
      case TargetRef.ArrayComponent arrayComponent ->
          resolveArrayComponent(arrayComponent, context);
      case TargetRef.Local local -> resolveLocal(local, context);
      case TargetRef.Receiver receiver -> NON_NULL_CONTRACT;
    };
  }

  private ValueContract resolveMethodParameter(TargetRef.MethodParameter target) {
    MethodTypeDesc descriptor = target.method().methodTypeSymbol();
    String parameterDescriptor =
        descriptor.parameterList().get(target.parameterIndex()).descriptorString();
    if (!isReferenceDescriptor(parameterDescriptor)) {
      return ValueContract.none();
    }
    AnnotatedTypeUse typeUse = methodParameterTypeUse(target.method(), target.parameterIndex());
    return resolveAnnotations(typeUse.rootAnnotations(), true);
  }

  private ValueContract resolveMethodReturn(TargetRef.MethodReturn target) {
    String descriptor = target.method().methodTypeSymbol().returnType().descriptorString();
    if (!isReferenceDescriptor(descriptor)) {
      return ValueContract.none();
    }
    return resolveAnnotations(methodReturnTypeUse(target.method()).rootAnnotations(), true);
  }

  private ValueContract resolveInvokedMethod(
      TargetRef.InvokedMethod target, ResolutionContext context) {
    String returnDescriptor = target.descriptor().returnType().descriptorString();
    if (!isReferenceDescriptor(returnDescriptor)) {
      return ValueContract.none();
    }

    return resolveAnnotations(
        context
            .resolutionEnvironment()
            .findDeclaredMethod(
                target.ownerInternalName(),
                target.methodName(),
                target.descriptor().descriptorString(),
                context.loader())
            .map(method -> methodReturnTypeUse(method).rootAnnotations())
            .orElse(List.of()),
        true);
  }

  private ValueContract resolveField(TargetRef.Field target, ResolutionContext context) {
    if (!isReferenceDescriptor(target.descriptor())) {
      return ValueContract.none();
    }

    return resolveAnnotations(
        context
            .resolutionEnvironment()
            .findDeclaredField(target.ownerInternalName(), target.fieldName(), context.loader())
            .map(field -> fieldTypeUse(field).rootAnnotations())
            .orElse(List.of()),
        true);
  }

  private ValueContract resolveArrayComponent(
      TargetRef.ArrayComponent target, ResolutionContext context) {
    if (!isReferenceArrayComponent(target.arrayDescriptor())) {
      return ValueContract.none();
    }

    AnnotatedTypeUse componentType = arrayComponentTypeUse(target, context);
    if (componentType == null) {
      return NON_NULL_CONTRACT;
    }
    return resolveAnnotations(componentType.rootAnnotations(), true);
  }

  private ValueContract resolveLocal(TargetRef.Local target, ResolutionContext context) {
    AnnotatedTypeUse typeUse = localTypeUse(target, null, context);
    return resolveAnnotations(typeUse.rootAnnotations(), true);
  }

  private AnnotatedTypeUse arrayComponentTypeUse(
      TargetRef.ArrayComponent target, ResolutionContext context) {
    AnnotatedTypeUse parentType =
        arraySourceTypeUse(target.arrayTarget(), target.arrayDescriptor(), context);
    if (parentType == null || !target.arrayDescriptor().startsWith("[")) {
      return null;
    }

    String componentDescriptor = target.arrayDescriptor().substring(1);
    List<Annotation> rootAnnotations = new ArrayList<>();
    List<TypeUseAnnotation> remainingTypeAnnotations = new ArrayList<>();
    for (TypeUseAnnotation typeAnnotation : parentType.typeAnnotations()) {
      if (!startsWithArrayStep(typeAnnotation.targetPath())) {
        continue;
      }
      List<TypeAnnotation.TypePathComponent> remainingPath =
          typeAnnotation.targetPath().subList(1, typeAnnotation.targetPath().size());
      if (remainingPath.isEmpty()) {
        rootAnnotations.add(typeAnnotation.annotation());
      }
      remainingTypeAnnotations.add(
          new TypeUseAnnotation(typeAnnotation.annotation(), List.copyOf(remainingPath)));
    }
    return new AnnotatedTypeUse(
        componentDescriptor, List.copyOf(rootAnnotations), List.copyOf(remainingTypeAnnotations));
  }

  private AnnotatedTypeUse arraySourceTypeUse(
      TargetRef sourceTarget, String descriptorHint, ResolutionContext context) {
    if (sourceTarget == null) {
      return new AnnotatedTypeUse(descriptorHint, List.of(), List.of());
    }

    return switch (sourceTarget) {
      case TargetRef.MethodParameter methodParameter ->
          methodParameterTypeUse(methodParameter.method(), methodParameter.parameterIndex());
      case TargetRef.MethodReturn methodReturn -> methodReturnTypeUse(methodReturn.method());
      case TargetRef.InvokedMethod invokedMethod -> invokedMethodTypeUse(invokedMethod, context);
      case TargetRef.Field field -> fieldTypeUse(field, context);
      case TargetRef.ArrayComponent arrayComponent ->
          arrayComponentTypeUse(arrayComponent, context);
      case TargetRef.Local local -> localTypeUse(local, descriptorHint, context);
      case TargetRef.Receiver receiver ->
          new AnnotatedTypeUse("L" + receiver.ownerInternalName() + ";", List.of(), List.of());
    };
  }

  private AnnotatedTypeUse methodParameterTypeUse(MethodModel method, int parameterIndex) {
    String descriptor =
        method.methodTypeSymbol().parameterList().get(parameterIndex).descriptorString();
    List<Annotation> rootAnnotations = new ArrayList<>();
    List<TypeUseAnnotation> typeAnnotations = new ArrayList<>();

    method
        .findAttribute(Attributes.runtimeVisibleParameterAnnotations())
        .ifPresent(
            attr -> {
              List<List<Annotation>> all = attr.parameterAnnotations();
              if (parameterIndex < all.size()) {
                rootAnnotations.addAll(all.get(parameterIndex));
              }
            });
    method
        .findAttribute(Attributes.runtimeVisibleTypeAnnotations())
        .ifPresent(
            attr -> {
              for (TypeAnnotation typeAnnotation : attr.annotations()) {
                if (typeAnnotation.targetInfo()
                        instanceof TypeAnnotation.FormalParameterTarget target
                    && target.formalParameterIndex() == parameterIndex) {
                  addTypeAnnotation(rootAnnotations, typeAnnotations, typeAnnotation);
                }
              }
            });
    return new AnnotatedTypeUse(
        descriptor, List.copyOf(rootAnnotations), List.copyOf(typeAnnotations));
  }

  private AnnotatedTypeUse methodReturnTypeUse(MethodModel method) {
    String descriptor = method.methodTypeSymbol().returnType().descriptorString();
    List<Annotation> rootAnnotations = new ArrayList<>();
    List<TypeUseAnnotation> typeAnnotations = new ArrayList<>();

    method
        .findAttribute(Attributes.runtimeVisibleAnnotations())
        .ifPresent(attr -> rootAnnotations.addAll(attr.annotations()));
    method
        .findAttribute(Attributes.runtimeVisibleTypeAnnotations())
        .ifPresent(
            attr -> {
              for (TypeAnnotation typeAnnotation : attr.annotations()) {
                if (typeAnnotation.targetInfo().targetType()
                    == TypeAnnotation.TargetType.METHOD_RETURN) {
                  addTypeAnnotation(rootAnnotations, typeAnnotations, typeAnnotation);
                }
              }
            });
    return new AnnotatedTypeUse(
        descriptor, List.copyOf(rootAnnotations), List.copyOf(typeAnnotations));
  }

  private AnnotatedTypeUse invokedMethodTypeUse(
      TargetRef.InvokedMethod target, ResolutionContext context) {
    String descriptor = target.descriptor().returnType().descriptorString();
    return context
        .resolutionEnvironment()
        .findDeclaredMethod(
            target.ownerInternalName(),
            target.methodName(),
            target.descriptor().descriptorString(),
            context.loader())
        .map(this::methodReturnTypeUse)
        .orElse(new AnnotatedTypeUse(descriptor, List.of(), List.of()));
  }

  private AnnotatedTypeUse fieldTypeUse(TargetRef.Field target, ResolutionContext context) {
    return context
        .resolutionEnvironment()
        .findDeclaredField(target.ownerInternalName(), target.fieldName(), context.loader())
        .map(this::fieldTypeUse)
        .orElse(new AnnotatedTypeUse(target.descriptor(), List.of(), List.of()));
  }

  private AnnotatedTypeUse fieldTypeUse(FieldModel field) {
    String descriptor = field.fieldType().stringValue();
    List<Annotation> rootAnnotations = new ArrayList<>();
    List<TypeUseAnnotation> typeAnnotations = new ArrayList<>();

    field
        .findAttribute(Attributes.runtimeVisibleAnnotations())
        .ifPresent(attr -> rootAnnotations.addAll(attr.annotations()));
    field
        .findAttribute(Attributes.runtimeVisibleTypeAnnotations())
        .ifPresent(
            attr -> {
              for (TypeAnnotation typeAnnotation : attr.annotations()) {
                if (typeAnnotation.targetInfo().targetType() == TypeAnnotation.TargetType.FIELD) {
                  addTypeAnnotation(rootAnnotations, typeAnnotations, typeAnnotation);
                }
              }
            });
    return new AnnotatedTypeUse(
        descriptor, List.copyOf(rootAnnotations), List.copyOf(typeAnnotations));
  }

  private AnnotatedTypeUse localTypeUse(
      TargetRef.Local target, String descriptorHint, ResolutionContext context) {
    List<Annotation> rootAnnotations = new ArrayList<>();
    List<TypeUseAnnotation> typeAnnotations = new ArrayList<>();
    for (ResolutionEnvironment.LocalVariableTypeAnnotation localAnnotation :
        context
            .resolutionEnvironment()
            .localsAt(target.method(), target.bytecodeIndex(), target.slot())) {
      addTypeAnnotation(rootAnnotations, typeAnnotations, localAnnotation);
    }
    return new AnnotatedTypeUse(
        descriptorHint != null ? descriptorHint : "Ljava/lang/Object;",
        List.copyOf(rootAnnotations),
        List.copyOf(typeAnnotations));
  }

  private void addTypeAnnotation(
      List<Annotation> rootAnnotations,
      List<TypeUseAnnotation> typeAnnotations,
      TypeAnnotation typeAnnotation) {
    List<TypeAnnotation.TypePathComponent> targetPath = List.copyOf(typeAnnotation.targetPath());
    if (targetPath.isEmpty()) {
      rootAnnotations.add(typeAnnotation.annotation());
    }
    typeAnnotations.add(new TypeUseAnnotation(typeAnnotation.annotation(), targetPath));
  }

  private void addTypeAnnotation(
      List<Annotation> rootAnnotations,
      List<TypeUseAnnotation> typeAnnotations,
      ResolutionEnvironment.LocalVariableTypeAnnotation localAnnotation) {
    List<TypeAnnotation.TypePathComponent> targetPath = List.copyOf(localAnnotation.targetPath());
    if (targetPath.isEmpty()) {
      rootAnnotations.add(localAnnotation.annotation());
    }
    typeAnnotations.add(new TypeUseAnnotation(localAnnotation.annotation(), targetPath));
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

  private boolean isReferenceDescriptor(String descriptor) {
    return descriptor.startsWith("L") || descriptor.startsWith("[");
  }

  private boolean isReferenceArrayComponent(String arrayDescriptor) {
    return arrayDescriptor.startsWith("[L") || arrayDescriptor.startsWith("[[");
  }

  private boolean startsWithArrayStep(List<TypeAnnotation.TypePathComponent> targetPath) {
    return !targetPath.isEmpty()
        && targetPath.get(0).typePathKind() == TypeAnnotation.TypePathComponent.Kind.ARRAY;
  }

  private record AnnotatedTypeUse(
      String descriptor,
      List<Annotation> rootAnnotations,
      List<TypeUseAnnotation> typeAnnotations) {}

  private record TypeUseAnnotation(
      Annotation annotation, List<TypeAnnotation.TypePathComponent> targetPath) {}
}
