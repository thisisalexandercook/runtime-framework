package io.github.eisop.runtimeframework.agent;

import io.github.eisop.runtimeframework.core.RuntimeChecker;
import io.github.eisop.runtimeframework.filter.FrameworkSafetyFilter;
import java.lang.instrument.Instrumentation;

public final class RuntimeAgent {

  public static void premain(String args, Instrumentation inst) {
    // 1. Setup Filter
    var filter = new FrameworkSafetyFilter();

    // 2. Load Checker Dynamically
    // Default to SysOut if nothing specified
    String checkerClassName =
        System.getProperty(
            "runtime.checker", "io.github.eisop.runtimeframework.util.SysOutRuntimeChecker");

    RuntimeChecker checker;
    try {
      System.out.println("[RuntimeAgent] Loading checker: " + checkerClassName);
      Class<?> clazz = Class.forName(checkerClassName);
      checker = (RuntimeChecker) clazz.getConstructor().newInstance();
    } catch (Exception e) {
      System.err.println(
          "[RuntimeAgent] FATAL: Could not instantiate checker: " + checkerClassName);
      e.printStackTrace();
      return; // Abort agent
    }

    // 3. Register
    inst.addTransformer(new RuntimeTransformer(filter, checker), false);
  }
}
