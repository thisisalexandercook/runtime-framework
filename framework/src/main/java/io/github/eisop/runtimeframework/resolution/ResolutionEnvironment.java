package io.github.eisop.runtimeframework.resolution;

import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeAnnotation;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Shared environment for bytecode metadata lookup.
 *
 * <p>This centralizes loader-aware {@link ClassModel} loading and related member lookups so
 * strategies, filters, and hierarchy analysis do not each parse classfiles independently.
 */
public interface ResolutionEnvironment {

  /**
   * Loads a class model for an internal name like {@code pkg/Foo}.
   *
   * @param internalName JVM internal class name
   * @param loader class loader used to resolve the class bytes
   * @return the parsed class model if available
   */
  Optional<ClassModel> loadClass(String internalName, ClassLoader loader);

  default Optional<ClassModel> loadSuperclass(ClassModel model, ClassLoader loader) {
    return model.superclass().flatMap(superClass -> loadClass(superClass.asInternalName(), loader));
  }

  default Optional<FieldModel> findDeclaredField(
      String ownerInternalName, String fieldName, ClassLoader loader) {
    return loadClass(ownerInternalName, loader)
        .flatMap(
            model ->
                model.fields().stream()
                    .filter(field -> field.fieldName().stringValue().equals(fieldName))
                    .findFirst());
  }

  default Optional<MethodModel> findDeclaredMethod(
      String ownerInternalName, String methodName, String descriptor, ClassLoader loader) {
    return loadClass(ownerInternalName, loader)
        .flatMap(
            model ->
                model.methods().stream()
                    .filter(method -> method.methodName().stringValue().equals(methodName))
                    .filter(
                        method -> method.methodTypeSymbol().descriptorString().equals(descriptor))
                    .findFirst());
  }

  default Optional<ResolvedMethod> findResolvedVirtualMethod(
      String ownerInternalName, String methodName, String descriptor, ClassLoader loader) {
    List<ClassModel> hierarchy = new ArrayList<>();
    Optional<ClassModel> current = loadClass(ownerInternalName, loader);
    while (current.isPresent()) {
      ClassModel model = current.get();
      hierarchy.add(model);
      Optional<MethodModel> method = findMethod(model, methodName, descriptor);
      if (method.isPresent()) {
        return Optional.of(
            new ResolvedMethod(model.thisClass().asInternalName(), model, method.get()));
      }
      current = loadSuperclass(model, loader);
    }

    Set<String> visitedInterfaces = new HashSet<>();
    for (ClassModel model : hierarchy) {
      Optional<ResolvedMethod> defaultMethod =
          findResolvedInterfaceDefaultFromClass(
              model, methodName, descriptor, loader, visitedInterfaces);
      if (defaultMethod.isPresent()) {
        return defaultMethod;
      }
    }

    return Optional.empty();
  }

  default Optional<ResolvedMethod> findResolvedInterfaceMethod(
      String ownerInternalName, String methodName, String descriptor, ClassLoader loader) {
    return loadClass(ownerInternalName, loader)
        .flatMap(
            model ->
                findResolvedInterfaceMethod(
                    model, methodName, descriptor, loader, new HashSet<>()));
  }

  private Optional<ResolvedMethod> findResolvedInterfaceMethod(
      ClassModel model,
      String methodName,
      String descriptor,
      ClassLoader loader,
      Set<String> visited) {
    String internalName = model.thisClass().asInternalName();
    if (!visited.add(internalName)) {
      return Optional.empty();
    }

    Optional<MethodModel> method = findMethod(model, methodName, descriptor);
    if (method.isPresent()) {
      return Optional.of(new ResolvedMethod(internalName, model, method.get()));
    }

    for (var parent : model.interfaces()) {
      Optional<ResolvedMethod> resolved =
          loadClass(parent.asInternalName(), loader)
              .flatMap(
                  parentModel ->
                      findResolvedInterfaceMethod(
                          parentModel, methodName, descriptor, loader, visited));
      if (resolved.isPresent()) {
        return resolved;
      }
    }
    return Optional.empty();
  }

  private Optional<MethodModel> findMethod(
      ClassModel model, String methodName, String descriptor) {
    return model.methods().stream()
        .filter(method -> method.methodName().stringValue().equals(methodName))
        .filter(method -> method.methodTypeSymbol().descriptorString().equals(descriptor))
        .findFirst();
  }

  private Optional<ResolvedMethod> findResolvedInterfaceDefaultFromClass(
      ClassModel classModel,
      String methodName,
      String descriptor,
      ClassLoader loader,
      Set<String> visitedInterfaces) {
    for (var interfaceEntry : classModel.interfaces()) {
      Optional<ResolvedMethod> resolved =
          loadClass(interfaceEntry.asInternalName(), loader)
              .flatMap(
                  interfaceModel ->
                      findResolvedInterfaceDefaultMethod(
                          interfaceModel, methodName, descriptor, loader, visitedInterfaces));
      if (resolved.isPresent()) {
        return resolved;
      }
    }
    return Optional.empty();
  }

  private Optional<ResolvedMethod> findResolvedInterfaceDefaultMethod(
      ClassModel interfaceModel,
      String methodName,
      String descriptor,
      ClassLoader loader,
      Set<String> visitedInterfaces) {
    String internalName = interfaceModel.thisClass().asInternalName();
    if (!visitedInterfaces.add(internalName)) {
      return Optional.empty();
    }

    Optional<MethodModel> candidate = findMethod(interfaceModel, methodName, descriptor);
    if (candidate.isPresent() && isInterfaceDefaultMethod(candidate.get())) {
      return Optional.of(new ResolvedMethod(internalName, interfaceModel, candidate.get()));
    }

    for (var parent : interfaceModel.interfaces()) {
      Optional<ResolvedMethod> resolved =
          loadClass(parent.asInternalName(), loader)
              .flatMap(
                  parentModel ->
                      findResolvedInterfaceDefaultMethod(
                          parentModel, methodName, descriptor, loader, visitedInterfaces));
      if (resolved.isPresent()) {
        return resolved;
      }
    }

    return Optional.empty();
  }

  private boolean isInterfaceDefaultMethod(MethodModel method) {
    int flags = method.flags().flagsMask();
    return method.code().isPresent()
        && !Modifier.isStatic(flags)
        && !Modifier.isPrivate(flags)
        && !Modifier.isAbstract(flags);
  }

  /**
   * Returns local-variable type annotations for a specific slot.
   *
   * <p>The returned bindings preserve live-range labels so later phases can resolve the active
   * local at a particular bytecode location.
   */
  List<LocalVariableTypeAnnotation> getLocalVariableTypeAnnotations(MethodModel method, int slot);

  default List<LocalVariableTypeAnnotation> localsAt(
      MethodModel method, int bytecodeOffset, int slot) {
    return getLocalVariableTypeAnnotations(method, slot).stream()
        .filter(binding -> binding.contains(bytecodeOffset, method))
        .toList();
  }

  static ResolutionEnvironment system() {
    return Holder.INSTANCE;
  }

  record LocalVariableTypeAnnotation(
      TypeAnnotation typeAnnotation, Label startLabel, Label endLabel, int slot) {

    public java.lang.classfile.Annotation annotation() {
      return typeAnnotation.annotation();
    }

    public List<TypeAnnotation.TypePathComponent> targetPath() {
      return typeAnnotation.targetPath();
    }

    public boolean contains(int bytecodeOffset, MethodModel method) {
      if (bytecodeOffset < 0) {
        return true;
      }
      return method
          .code()
          .filter(java.lang.classfile.attribute.CodeAttribute.class::isInstance)
          .map(java.lang.classfile.attribute.CodeAttribute.class::cast)
          .map(
              codeAttribute -> {
                int startOffset = codeAttribute.labelToBci(startLabel);
                int endOffset = codeAttribute.labelToBci(endLabel);
                return startOffset <= bytecodeOffset && bytecodeOffset < endOffset;
              })
          .orElse(false);
    }
  }

  record ResolvedMethod(String ownerInternalName, ClassModel ownerModel, MethodModel method) {}

  final class Holder {
    private static final ResolutionEnvironment INSTANCE = new CachingResolutionEnvironment();

    private Holder() {}
  }
}
