package io.github.eisop.runtimeframework.agent;

import java.lang.instrument.Instrumentation;
import java.util.List;

public final class RuntimeAgent {

  public static void premain(String args, Instrumentation inst) {
    inst.addTransformer(new ClassReader(List.of("HelloWorld")), false);
  }
}
