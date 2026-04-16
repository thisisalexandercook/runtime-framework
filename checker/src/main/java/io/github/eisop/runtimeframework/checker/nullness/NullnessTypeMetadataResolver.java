package io.github.eisop.runtimeframework.checker.nullness;

import io.github.eisop.runtimeframework.planning.TargetRef;
import io.github.eisop.runtimeframework.resolution.ResolutionEnvironment;
import io.github.eisop.runtimeframework.semantics.ResolutionContext;
import io.github.eisop.runtimeframework.semantics.TypeMetadataResolver;
import io.github.eisop.runtimeframework.semantics.TypeUseMetadata;
import io.github.eisop.runtimeframework.semantics.TypeUseQualifier;
import java.lang.classfile.Attributes;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeAnnotation;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Resolves nullness-specific type-use metadata, including default qualifiers. */
public final class NullnessTypeMetadataResolver implements TypeMetadataResolver {

  private static final String NON_NULL_DESC = NonNull.class.descriptorString();
  private static final String NULLABLE_DESC = Nullable.class.descriptorString();

  @Override
  public TypeUseMetadata resolve(TargetRef target, ResolutionContext context) {
    return switch (target) {
      case TargetRef.MethodParameter methodParameter ->
          applyNullnessDefault(
              methodParameterTypeUse(methodParameter.method(), methodParameter.parameterIndex()));
      case TargetRef.MethodReturn methodReturn ->
          applyNullnessDefault(methodReturnTypeUse(methodReturn.method()));
      case TargetRef.InvokedMethod invokedMethod ->
          applyNullnessDefault(invokedMethodTypeUse(invokedMethod, context));
      case TargetRef.Field field -> applyNullnessDefault(fieldTypeUse(field, context));
      case TargetRef.ArrayComponent arrayComponent ->
          applyNullnessDefault(arrayComponentTypeUse(arrayComponent, context));
      case TargetRef.Local local ->
          applyNullnessDefault(localTypeUse(local, "Ljava/lang/Object;", context));
      case TargetRef.Receiver receiver ->
          TypeUseMetadata.empty("L" + receiver.ownerInternalName() + ";")
              .withRootQualifier(NON_NULL_DESC, true);
    };
  }

  private TypeUseMetadata applyNullnessDefault(TypeUseMetadata metadata) {
    if (!metadata.isReferenceType()) {
      return metadata;
    }
    if (metadata.hasRootQualifier(NON_NULL_DESC) || metadata.hasRootQualifier(NULLABLE_DESC)) {
      return metadata;
    }
    return metadata.withRootQualifier(NON_NULL_DESC, true);
  }

  private TypeUseMetadata arrayComponentTypeUse(
      TargetRef.ArrayComponent target, ResolutionContext context) {
    if (!isReferenceArrayComponent(target.arrayDescriptor())) {
      return TypeUseMetadata.empty(
          target.arrayDescriptor().startsWith("[")
              ? target.arrayDescriptor().substring(1)
              : target.arrayDescriptor());
    }

    return arraySourceTypeUse(target.arrayTarget(), target.arrayDescriptor(), context)
        .arrayComponent();
  }

  private TypeUseMetadata arraySourceTypeUse(
      TargetRef sourceTarget, String descriptorHint, ResolutionContext context) {
    if (sourceTarget == null) {
      return TypeUseMetadata.empty(descriptorHint);
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
          TypeUseMetadata.empty("L" + receiver.ownerInternalName() + ";");
    };
  }

  private TypeUseMetadata methodParameterTypeUse(MethodModel method, int parameterIndex) {
    String descriptor =
        method.methodTypeSymbol().parameterList().get(parameterIndex).descriptorString();
    if (!isReferenceDescriptor(descriptor)) {
      return TypeUseMetadata.empty(descriptor);
    }

    List<TypeUseQualifier> qualifiers = new ArrayList<>();
    method
        .findAttribute(Attributes.runtimeVisibleParameterAnnotations())
        .ifPresent(
            attr -> {
              var all = attr.parameterAnnotations();
              if (parameterIndex < all.size()) {
                all.get(parameterIndex)
                    .forEach(annotation -> addRootQualifier(qualifiers, annotation));
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
                  addQualifier(qualifiers, typeAnnotation);
                }
              }
            });
    return new TypeUseMetadata(descriptor, qualifiers);
  }

  private TypeUseMetadata methodReturnTypeUse(MethodModel method) {
    String descriptor = method.methodTypeSymbol().returnType().descriptorString();
    if (!isReferenceDescriptor(descriptor)) {
      return TypeUseMetadata.empty(descriptor);
    }

    List<TypeUseQualifier> qualifiers = new ArrayList<>();
    method
        .findAttribute(Attributes.runtimeVisibleAnnotations())
        .ifPresent(
            attr ->
                attr.annotations().forEach(annotation -> addRootQualifier(qualifiers, annotation)));
    method
        .findAttribute(Attributes.runtimeVisibleTypeAnnotations())
        .ifPresent(
            attr -> {
              for (TypeAnnotation typeAnnotation : attr.annotations()) {
                if (typeAnnotation.targetInfo().targetType()
                    == TypeAnnotation.TargetType.METHOD_RETURN) {
                  addQualifier(qualifiers, typeAnnotation);
                }
              }
            });
    return new TypeUseMetadata(descriptor, qualifiers);
  }

  private TypeUseMetadata invokedMethodTypeUse(
      TargetRef.InvokedMethod target, ResolutionContext context) {
    String descriptor = target.descriptor().returnType().descriptorString();
    if (!isReferenceDescriptor(descriptor)) {
      return TypeUseMetadata.empty(descriptor);
    }

    return context
        .resolutionEnvironment()
        .findDeclaredMethod(
            target.ownerInternalName(),
            target.methodName(),
            target.descriptor().descriptorString(),
            context.loader())
        .map(this::methodReturnTypeUse)
        .orElse(TypeUseMetadata.empty(descriptor));
  }

  private TypeUseMetadata fieldTypeUse(TargetRef.Field target, ResolutionContext context) {
    if (!isReferenceDescriptor(target.descriptor())) {
      return TypeUseMetadata.empty(target.descriptor());
    }

    return context
        .resolutionEnvironment()
        .findDeclaredField(target.ownerInternalName(), target.fieldName(), context.loader())
        .map(this::fieldTypeUse)
        .orElse(TypeUseMetadata.empty(target.descriptor()));
  }

  private TypeUseMetadata fieldTypeUse(FieldModel field) {
    String descriptor = field.fieldType().stringValue();
    if (!isReferenceDescriptor(descriptor)) {
      return TypeUseMetadata.empty(descriptor);
    }

    List<TypeUseQualifier> qualifiers = new ArrayList<>();
    field
        .findAttribute(Attributes.runtimeVisibleAnnotations())
        .ifPresent(
            attr ->
                attr.annotations().forEach(annotation -> addRootQualifier(qualifiers, annotation)));
    field
        .findAttribute(Attributes.runtimeVisibleTypeAnnotations())
        .ifPresent(
            attr -> {
              for (TypeAnnotation typeAnnotation : attr.annotations()) {
                if (typeAnnotation.targetInfo().targetType() == TypeAnnotation.TargetType.FIELD) {
                  addQualifier(qualifiers, typeAnnotation);
                }
              }
            });
    return new TypeUseMetadata(descriptor, qualifiers);
  }

  private TypeUseMetadata localTypeUse(
      TargetRef.Local target, String descriptorHint, ResolutionContext context) {
    List<TypeUseQualifier> qualifiers = new ArrayList<>();
    for (ResolutionEnvironment.LocalVariableTypeAnnotation localAnnotation :
        context
            .resolutionEnvironment()
            .localsAt(target.method(), target.bytecodeIndex(), target.slot())) {
      addQualifier(qualifiers, localAnnotation);
    }
    return new TypeUseMetadata(descriptorHint, qualifiers);
  }

  private void addRootQualifier(
      List<TypeUseQualifier> qualifiers, java.lang.classfile.Annotation annotation) {
    qualifiers.add(
        new TypeUseQualifier(annotation.classSymbol().descriptorString(), List.of(), false));
  }

  private void addQualifier(List<TypeUseQualifier> qualifiers, TypeAnnotation typeAnnotation) {
    qualifiers.add(
        new TypeUseQualifier(
            typeAnnotation.annotation().classSymbol().descriptorString(),
            List.copyOf(typeAnnotation.targetPath()),
            false));
  }

  private void addQualifier(
      List<TypeUseQualifier> qualifiers,
      ResolutionEnvironment.LocalVariableTypeAnnotation localAnnotation) {
    qualifiers.add(
        new TypeUseQualifier(
            localAnnotation.annotation().classSymbol().descriptorString(),
            List.copyOf(localAnnotation.targetPath()),
            false));
  }

  private boolean isReferenceDescriptor(String descriptor) {
    return descriptor.startsWith("L") || descriptor.startsWith("[");
  }

  private boolean isReferenceArrayComponent(String arrayDescriptor) {
    return arrayDescriptor.startsWith("[L") || arrayDescriptor.startsWith("[[");
  }
}
