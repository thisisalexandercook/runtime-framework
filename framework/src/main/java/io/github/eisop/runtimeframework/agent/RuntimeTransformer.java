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

    // IGNORE JDK INTERNALS to avoid crashing the console
    if (className != null
        && (className.startsWith("java/")
            || className.startsWith("sun/")
            || className.startsWith("jdk/")
            || className.startsWith("org/gradle"))) {
      return null;
    }

    try {
      ClassInfo info = new ClassInfo(className, loader, module);
      boolean accepted = filter.test(info);

      if (!accepted) {
        System.out.println("[RuntimeFramework] -> REJECTED by filter");
        return null;
      }

      System.out.println("[RuntimeFramework] -> ACCEPTED. Instrumenting...");

      ClassFile cf = ClassFile.of();
      ClassModel classModel = cf.parse(classfileBuffer);
      RuntimeInstrumenter instrumenter = checker.getInstrumenter(filter);
      return cf.transformClass(classModel, instrumenter.asClassTransform(classModel, loader));

    } catch (Throwable t) {
      System.err.println("[RuntimeFramework] CRASH transforming: " + className);
      t.printStackTrace();
      return null;
    }
  }
}
