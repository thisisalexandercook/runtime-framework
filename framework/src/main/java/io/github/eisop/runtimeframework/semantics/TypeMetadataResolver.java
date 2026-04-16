package io.github.eisop.runtimeframework.semantics;

import io.github.eisop.runtimeframework.planning.TargetRef;

/** Resolves checker-specific type-use metadata for runtime flow targets. */
public interface TypeMetadataResolver {

  TypeUseMetadata resolve(TargetRef target, ResolutionContext context);

  static TypeMetadataResolver none() {
    return (target, context) -> TypeUseMetadata.empty(descriptorOf(target));
  }

  private static String descriptorOf(TargetRef target) {
    return switch (target) {
      case TargetRef.MethodParameter methodParameter ->
          methodParameter
              .method()
              .methodTypeSymbol()
              .parameterList()
              .get(methodParameter.parameterIndex())
              .descriptorString();
      case TargetRef.MethodReturn methodReturn ->
          methodReturn.method().methodTypeSymbol().returnType().descriptorString();
      case TargetRef.InvokedMethod invokedMethod ->
          invokedMethod.descriptor().returnType().descriptorString();
      case TargetRef.Field field -> field.descriptor();
      case TargetRef.ArrayComponent arrayComponent ->
          arrayComponent.arrayDescriptor().startsWith("[")
              ? arrayComponent.arrayDescriptor().substring(1)
              : arrayComponent.arrayDescriptor();
      case TargetRef.Local ignored -> "Ljava/lang/Object;";
      case TargetRef.Receiver receiver -> "L" + receiver.ownerInternalName() + ";";
    };
  }
}
