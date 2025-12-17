package io.github.eisop.runtimeframework.agent;

import io.github.eisop.runtimeframework.core.RuntimeChecker;
import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.ClassListFilter;
import io.github.eisop.runtimeframework.filter.Filter;
import io.github.eisop.runtimeframework.filter.FrameworkSafetyFilter;
import java.lang.instrument.Instrumentation;
import java.util.Arrays;

public final class RuntimeAgent {

  public static void premain(String args, Instrumentation inst) {
    // 1. Safety Filter (Always active to protect JDK/Framework)
    Filter<ClassInfo> safeFilter = new FrameworkSafetyFilter();

    // 2. Defaults
    Filter<ClassInfo> policyFilter = safeFilter; // "Checked" scope
    Filter<ClassInfo> scanFilter = safeFilter; // "Instrumentation" scope

    // 3. User Configuration: Checked List
    String checkedClasses = System.getProperty("runtime.classes");
    if (checkedClasses != null && !checkedClasses.isBlank()) {
      System.out.println("[RuntimeAgent] Checked Scope restricted to: " + checkedClasses);
      Filter<ClassInfo> listFilter = new ClassListFilter(Arrays.asList(checkedClasses.split(",")));

      // Policy: Must be Safe AND in List
      policyFilter = info -> safeFilter.test(info) && listFilter.test(info);
      // Default Scan: Matches Policy
      scanFilter = policyFilter;
    }

    // 4. User Configuration: Global Mode
    // If enabled, we scan EVERYTHING safe, even if it's not in the Checked List.
    // This allows us to instrument LegacyLib to catch writes to Checked code.
    if (Boolean.getBoolean("runtime.global")) {
      System.out.println("[RuntimeAgent] Global Mode ENABLED. Scanning all safe classes.");
      scanFilter = safeFilter;
    }

    // 5. Load Checker
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
      return;
    }

    inst.addTransformer(new RuntimeTransformer(scanFilter, policyFilter, checker), false);
  }
}
