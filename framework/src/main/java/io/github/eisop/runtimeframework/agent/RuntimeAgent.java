package io.github.eisop.runtimeframework.agent;

import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.Filter;
import io.github.eisop.runtimeframework.filter.ClassListFilter;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.List;

public final class RuntimeAgent {
  // Swap this to your real filter later
    private static final Filter<ClassInfo> CLASS_FILTER = new ClassListFilter(java.util.List.of("Hello"));

  public static void premain(String args, Instrumentation inst) {
    inst.addTransformer(new ClassfileTransformer(CLASS_FILTER), /*canRetransform=*/false);
  }

  static final class ClassfileTransformer implements ClassFileTransformer {
    private final Filter<ClassInfo> classFilter;

    ClassfileTransformer(Filter<ClassInfo> classFilter) {
      this.classFilter = classFilter;
    }

    @Override
    public byte[] transform(
        Module module,
        ClassLoader loader,
        String className,                 // internal name, e.g., "io/github/eisop/Foo"
        Class<?> classBeingRedefined,
        ProtectionDomain protectionDomain,
        byte[] classfileBuffer) {

      if (className == null) return null;

      ClassInfo ci = new ClassInfo(className, loader, module);

      // Gate by class-level filter
      if (!classFilter.test(ci)) return null;

      try {
        return TransformUtil.transformBytes(classfileBuffer);  // no-op/identity for now
      } catch (Throwable t) {
        System.err.println("[runtimeframework] transform failed for " + className + ": " + t);
        return null; // on error, don't block class loading
      }
    }
  }
}
