package io.github.eisop.runtimeframework.checker.nullness;

import io.github.eisop.runtimeframework.core.TargetAnnotation;
import java.lang.annotation.Annotation;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import org.checkerframework.checker.nullness.qual.NonNull;

public class NonNullTarget implements TargetAnnotation {

  private static final ClassDesc VERIFIER = ClassDesc.of(NullnessRuntimeVerifier.class.getName());
  private static final String METHOD = "checkNotNull";
  private static final MethodTypeDesc DESC =
      MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;Ljava/lang/String;)V");

  @Override
  public Class<? extends Annotation> annotationType() {
    return NonNull.class;
  }

  @Override
  public void check(CodeBuilder b, TypeKind type, String diagnosticName) {
    // FIX: Use TypeKind.Reference (correct enum name in JDK 25)
    if (type == TypeKind.REFERENCE) {
      // Stack: [..., Value]

      // 1. Push Message
      b.ldc(diagnosticName + " must be NonNull");

      // Stack: [..., Value, String]

      // 2. Call Verifier
      b.invokestatic(VERIFIER, METHOD, DESC);

      // Stack: [...] (Consumed)
    } else {
      // If it's a primitive, pop it to clean stack (no check needed)
      if (type.slotSize() == 1) b.pop();
      else b.pop2();
    }
  }
}
