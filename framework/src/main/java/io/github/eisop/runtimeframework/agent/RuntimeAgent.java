package io.github.eisop.runtimeframework.agent;

import io.github.eisop.runtimeframework.filter.FrameworkSafetyFilter;
import java.lang.instrument.Instrumentation;

public final class RuntimeAgent {

  public static void premain(String args, Instrumentation inst) {
    var filter = new FrameworkSafetyFilter();
    inst.addTransformer(new RuntimeTransformer(filter), false);
  }
}
