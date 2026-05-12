package io.github.eisop.runtimeframework.checker.nullness;

import io.github.eisop.runtimeframework.contracts.PropertyId;
import io.github.eisop.runtimeframework.contracts.PropertyRequirement;
import io.github.eisop.runtimeframework.planning.DiagnosticSpec;
import io.github.eisop.runtimeframework.planning.ValueAccess;
import io.github.eisop.runtimeframework.runtime.AttributionKind;
import io.github.eisop.runtimeframework.semantics.PropertyEmitter;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

/** Emits nullness checks for planner-native value actions. */
public final class NullnessPropertyEmitter implements PropertyEmitter {

  private static final ClassDesc VERIFIER = ClassDesc.of(NullnessRuntimeVerifier.class.getName());
  private static final ClassDesc ATTRIBUTION_KIND = ClassDesc.of(AttributionKind.class.getName());
  private static final MethodTypeDesc CHECK_DESCRIPTOR =
      MethodTypeDesc.ofDescriptor(
          "(Ljava/lang/Object;Ljava/lang/String;Lio/github/eisop/runtimeframework/runtime/AttributionKind;)V");

  @Override
  public void emitCheck(
      CodeBuilder builder,
      PropertyRequirement property,
      ValueAccess access,
      AttributionKind attribution,
      DiagnosticSpec diagnostic) {
    if (property.propertyId() != PropertyId.NON_NULL) {
      throw new IllegalArgumentException("Unsupported nullness property: " + property.propertyId());
    }

    switch (access) {
      case ValueAccess.LocalSlot localSlot -> {
        builder.aload(localSlot.slot());
        emitVerifierCall(builder, attribution, diagnostic);
      }
      case ValueAccess.ThisReference ignored -> {
        builder.aload(0);
        emitVerifierCall(builder, attribution, diagnostic);
      }
      case ValueAccess.OperandStack operandStack -> {
        if (operandStack.depthFromTop() != 0) {
          throw new IllegalStateException("Only top-of-stack access is currently supported");
        }
        builder.dup();
        emitVerifierCall(builder, attribution, diagnostic);
      }
      case ValueAccess.FieldWriteValue fieldWriteValue -> {
        if (fieldWriteValue.isStaticAccess()) {
          builder.dup();
          emitVerifierCall(builder, attribution, diagnostic);
        } else {
          builder.dup_x1();
          emitVerifierCall(builder, attribution, diagnostic);
          builder.swap();
        }
      }
    }
  }

  private void emitVerifierCall(
      CodeBuilder builder, AttributionKind attribution, DiagnosticSpec diagnostic) {
    builder.ldc(diagnostic.displayName() + " must be NonNull");
    builder.getstatic(
        ATTRIBUTION_KIND,
        attribution.name(),
        ClassDesc.ofDescriptor("Lio/github/eisop/runtimeframework/runtime/AttributionKind;"));
    builder.invokestatic(VERIFIER, "checkNotNull", CHECK_DESCRIPTOR);
  }
}
