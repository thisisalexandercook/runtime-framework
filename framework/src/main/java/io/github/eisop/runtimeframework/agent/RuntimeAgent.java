package io.github.eisop.runtimeframework.agent;

import java.lang.instrument.Instrumentation;

public final class RuntimeAgent {
  public static void premain(String args, Instrumentation inst) {
    inst.addTransformer(new ClassReader(), false);
  }
}
