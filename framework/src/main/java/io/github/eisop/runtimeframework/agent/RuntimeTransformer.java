package io.github.eisop.runtimeframework.agent;

import io.github.eisop.runtimeframework.core.RuntimeChecker;
import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.instrumentation.RuntimeInstrumenter;
import io.github.eisop.runtimeframework.policy.ClassClassification;
import io.github.eisop.runtimeframework.policy.RuntimePolicy;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class RuntimeTransformer implements ClassFileTransformer {

  private final RuntimePolicy policy;
  private final RuntimeInstrumenter instrumenter;

  public RuntimeTransformer(RuntimePolicy policy, RuntimeChecker checker) {
    this.policy = policy;
    this.instrumenter = checker.getInstrumenter(policy);
  }

  @Override
  public byte[] transform(
      Module module,
      ClassLoader loader,
      String className,
      Class<?> classBeingRedefined,
      ProtectionDomain protectionDomain,
      byte[] classfileBuffer) {

    if (className == null) {
      return null;
    }

    ClassInfo info = new ClassInfo(className, loader, module);

    try {
      ClassFile cf = ClassFile.of();
      ClassModel classModel = cf.parse(classfileBuffer);
      ClassClassification classification = policy.classify(info, classModel);

      if (classification == ClassClassification.SKIP) {
        return null;
      }

      boolean isCheckedScope = classification == ClassClassification.CHECKED;
      return cf.transformClass(
          classModel, instrumenter.asClassTransform(classModel, loader, isCheckedScope));

    } catch (Throwable t) {
      System.err.println("[RuntimeFramework] CRASH transforming: " + className);
      t.printStackTrace();
      return null;
    }
  }
}
