package io.github.eisop.runtimeframework.agent;

import io.github.eisop.runtimeframework.core.RuntimeChecker;
import io.github.eisop.runtimeframework.filter.FrameworkSafetyFilter;
import io.github.eisop.runtimeframework.util.SysOutRuntimeChecker;
import java.lang.instrument.Instrumentation;

public final class RuntimeAgent {

  public static void premain(String args, Instrumentation inst) {
    var filter = new FrameworkSafetyFilter();
    RuntimeChecker checker = new SysOutRuntimeChecker();
    inst.addTransformer(new RuntimeTransformer(filter, checker), false);
  }
}
