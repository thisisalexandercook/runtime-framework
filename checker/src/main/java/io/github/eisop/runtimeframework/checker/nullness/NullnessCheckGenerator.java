package io.github.eisop.runtimeframework.checker.nullness;

import io.github.eisop.runtimeframework.core.CheckGenerator;
import io.github.eisop.runtimeframework.runtime.AttributionKind;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

public class NullnessCheckGenerator implements CheckGenerator {

  private static final ClassDesc VERIFIER = ClassDesc.of(NullnessRuntimeVerifier.class.getName());
  private static final ClassDesc ATTRIBUTION_KIND = ClassDesc.of(AttributionKind.class.getName());

  private static final String METHOD_DEFAULT = "checkNotNull";
  private static final MethodTypeDesc DESC_DEFAULT =
      MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;Ljava/lang/String;)V");

  private static final String METHOD_ATTRIBUTED = "checkNotNull";
  private static final MethodTypeDesc DESC_ATTRIBUTED =
      MethodTypeDesc.ofDescriptor(
          "(Ljava/lang/Object;Ljava/lang/String;Lio/github/eisop/runtimeframework/runtime/AttributionKind;)V");

  private final AttributionKind attribution;

  public NullnessCheckGenerator() {
    this(AttributionKind.LOCAL);
  }

  public NullnessCheckGenerator(AttributionKind attribution) {
    this.attribution = attribution;
  }

  @Override
  public CheckGenerator withAttribution(AttributionKind kind) {
    return new NullnessCheckGenerator(kind);
  }

  @Override
  public void generateCheck(CodeBuilder b, TypeKind type, String diagnosticName) {
    if (type == TypeKind.REFERENCE) {
      b.ldc(diagnosticName + " must be NonNull");

      if (attribution == AttributionKind.LOCAL) {
        b.invokestatic(VERIFIER, METHOD_DEFAULT, DESC_DEFAULT);
      } else {
        b.getstatic(
            ATTRIBUTION_KIND,
            attribution.name(),
            ClassDesc.ofDescriptor("Lio/github/eisop/runtimeframework/runtime/AttributionKind;"));
        b.invokestatic(VERIFIER, METHOD_ATTRIBUTED, DESC_ATTRIBUTED);
      }
    } else {
      if (type.slotSize() == 1) b.pop();
      else b.pop2();
    }
  }
}
