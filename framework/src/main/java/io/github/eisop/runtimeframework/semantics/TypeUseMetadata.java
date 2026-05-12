package io.github.eisop.runtimeframework.semantics;

import java.lang.classfile.TypeAnnotation;
import java.util.List;
import java.util.Objects;

/** Checker-owned metadata about qualifiers attached to a JVM type use. */
public record TypeUseMetadata(String descriptor, List<TypeUseQualifier> qualifiers) {

  public TypeUseMetadata {
    Objects.requireNonNull(descriptor, "descriptor");
    qualifiers = List.copyOf(Objects.requireNonNull(qualifiers, "qualifiers"));
  }

  public static TypeUseMetadata empty(String descriptor) {
    return new TypeUseMetadata(descriptor, List.of());
  }

  public boolean isReferenceType() {
    return descriptor.startsWith("L") || descriptor.startsWith("[");
  }

  public List<TypeUseQualifier> rootQualifiers() {
    return qualifiers.stream().filter(TypeUseQualifier::isRoot).toList();
  }

  public boolean hasRootQualifier(String qualifierDescriptor) {
    return rootQualifiers().stream()
        .anyMatch(qualifier -> qualifier.descriptor().equals(qualifierDescriptor));
  }

  public TypeUseMetadata withRootQualifier(String qualifierDescriptor, boolean defaulted) {
    return new TypeUseMetadata(
        descriptor,
        append(qualifiers, new TypeUseQualifier(qualifierDescriptor, List.of(), defaulted)));
  }

  public TypeUseMetadata arrayComponent() {
    if (!descriptor.startsWith("[")) {
      return empty(descriptor);
    }

    String componentDescriptor = descriptor.substring(1);
    List<TypeUseQualifier> componentQualifiers =
        qualifiers.stream()
            .filter(qualifier -> startsWithArrayStep(qualifier.targetPath()))
            .map(
                qualifier ->
                    new TypeUseQualifier(
                        qualifier.descriptor(),
                        qualifier.targetPath().subList(1, qualifier.targetPath().size()),
                        qualifier.defaulted()))
            .toList();
    return new TypeUseMetadata(componentDescriptor, componentQualifiers);
  }

  private static boolean startsWithArrayStep(List<TypeAnnotation.TypePathComponent> targetPath) {
    return !targetPath.isEmpty()
        && targetPath.get(0).typePathKind() == TypeAnnotation.TypePathComponent.Kind.ARRAY;
  }

  private static <T> List<T> append(List<T> values, T value) {
    return java.util.stream.Stream.concat(values.stream(), java.util.stream.Stream.of(value))
        .toList();
  }
}
