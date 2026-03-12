package io.github.eisop.runtimeframework.resolution;

import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeAnnotation;
import java.util.List;
import java.util.Optional;

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
                    .filter(method -> method.methodTypeSymbol().descriptorString().equals(descriptor))
                    .findFirst());
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

  final class Holder {
    private static final ResolutionEnvironment INSTANCE = new CachingResolutionEnvironment();

    private Holder() {}
  }
}
