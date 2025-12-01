package io.github.eisop.runtimeframework.agent;

import io.github.eisop.runtimeframework.core.RuntimeChecker;
import io.github.eisop.runtimeframework.core.RuntimeInstrumenter;
import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.Filter;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class RuntimeTransformer implements ClassFileTransformer {

  private final Filter<ClassInfo> filter;
  private final RuntimeChecker checker;

  public RuntimeTransformer(Filter<ClassInfo> filter, RuntimeChecker checker) {
    this.filter = filter;
    this.checker = checker;
  }

  @Override
  public byte[] transform(
      Module module,
      ClassLoader loader,
      String className,
      Class<?> classBeingRedefined,
      ProtectionDomain protectionDomain,
      byte[] classfileBuffer) {

    // 1. Wrap context
    ClassInfo info = new ClassInfo(className, loader, module);

    // 2. Check Filter
    if (!filter.test(info)) {
      return null;
    }

    // 3. Output matched class
    System.out.println("[RuntimeFramework] Filter matched: " + className);

    // 4. Perform transform
    try {
      ClassFile cf = ClassFile.of();
      ClassModel classModel = cf.parse(classfileBuffer);
      RuntimeInstrumenter instrumenter = checker.getInstrumenter(filter);
      return cf.transformClass(classModel, instrumenter.asClassTransform(classModel, loader));

    } catch (Exception e) {
      System.err.println("[RuntimeFramework] Failed to parse: " + className);
      e.printStackTrace();
      return null;
    }
  }
}
