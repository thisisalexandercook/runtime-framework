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

  private final Filter<ClassInfo> scanFilter; // Which classes do we parse/instrument?
  private final Filter<ClassInfo>
      policyFilter; // Which classes are considered "Checked" by the policy?
  private final RuntimeChecker checker;

  public RuntimeTransformer(
      Filter<ClassInfo> scanFilter, Filter<ClassInfo> policyFilter, RuntimeChecker checker) {
    this.scanFilter = scanFilter;
    this.policyFilter = policyFilter;
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

    // Skip JDK internals to avoid boot loop issues
    if (className != null
        && (className.startsWith("java/")
            || className.startsWith("sun/")
            || className.startsWith("jdk/")
            || className.startsWith("org/gradle"))) {
      return null;
    }

    ClassInfo info = new ClassInfo(className, loader, module);

    // 1. Check Scanning Scope
    if (!scanFilter.test(info)) {
      return null;
    }

    System.out.println("[RuntimeFramework] Processing: " + className);

    try {
      ClassFile cf = ClassFile.of();
      ClassModel classModel = cf.parse(classfileBuffer);

      // 2. Pass the POLICY filter to the instrumenter factory
      // The instrumenter will use this to distinguish Checked vs Unchecked
      RuntimeInstrumenter instrumenter = checker.getInstrumenter(policyFilter);

      return cf.transformClass(classModel, instrumenter.asClassTransform(classModel, loader));

    } catch (Throwable t) {
      System.err.println("[RuntimeFramework] CRASH transforming: " + className);
      t.printStackTrace();
      return null;
    }
  }
}
