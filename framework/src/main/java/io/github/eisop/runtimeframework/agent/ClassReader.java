package io.github.eisop.runtimeframework.agent;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

final class ClassReader implements ClassFileTransformer {

  @Override
  public byte[] transform(
      Module module,
      ClassLoader loader,
      String internalName,
      Class<?> classBeingRedefined,
      ProtectionDomain pd,
      byte[] buf) {

    if (internalName == null) return null;

    System.out.printf(
        "[agent] %s (module=%s, loader=%s, size=%d)%n",
        internalName,
        module != null ? module.getName() : "null",
        loader != null ? loader.getClass().getName() : "bootstrap",
        buf != null ? buf.length : -1);

    return null;
  }
}
