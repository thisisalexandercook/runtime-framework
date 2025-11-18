package io.github.eisop.runtimeframework.agent;

public final class RuntimeAgent {
  public static void premain(String args, java.lang.instrument.Instrumentation inst) {
    inst.addTransformer(new ClassReader(), false);
  }
}
