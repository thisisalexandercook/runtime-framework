package io.github.eisop.runtimeframework.agent;

import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.ClassListFilter;
import io.github.eisop.runtimeframework.filter.Filter;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.List;

final class ClassReader implements ClassFileTransformer {

  private final Filter<ClassInfo> classFilter;

  ClassReader(List<String> onlyTheseInternalOrDotNames) {

    this.classFilter = new ClassListFilter(onlyTheseInternalOrDotNames);
  }

  @Override
  public byte[] transform(
      Module module,
      ClassLoader loader,
      String internalName,
      Class<?> classBeingRedefined,
      ProtectionDomain pd,
      byte[] buf) {

    if (internalName == null) return null;

    if (!classFilter.test(new ClassInfo(internalName, loader, module))) {

      return null; // ignore everything not on the allow-list
    }

    System.out.printf(
        "[agent] %s (module=%s, loader=%s, size=%d)%n",
        internalName,
        module != null ? module.getName() : "null",
        loader != null ? loader.getClass().getName() : "bootstrap",
        buf != null ? buf.length : -1);

    return null;
  }
}
