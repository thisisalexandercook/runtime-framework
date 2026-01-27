package io.github.eisop.runtimeframework.checker.nullness;

import io.github.eisop.runtimeframework.core.RuntimeVerifier;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

public class NullnessVerifier implements RuntimeVerifier {

  private static final ClassDesc VERIFIER = ClassDesc.of(NullnessRuntimeVerifier.class.getName());
  private static final String METHOD = "checkNotNull";
  private static final MethodTypeDesc DESC =
      MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;Ljava/lang/String;)V");

  @Override
  public void generateCheck(CodeBuilder b, TypeKind type, String diagnosticName) {
    if (type == TypeKind.REFERENCE) {
      b.ldc(diagnosticName + " must be NonNull");
      b.invokestatic(VERIFIER, METHOD, DESC);
    } else {
      if (type.slotSize() == 1) b.pop();
      else b.pop2();
    }
  }
}
