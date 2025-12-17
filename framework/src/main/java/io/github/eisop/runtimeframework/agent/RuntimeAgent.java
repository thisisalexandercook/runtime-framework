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
    // 1. Safety Filter
    Filter<ClassInfo> safeFilter = new FrameworkSafetyFilter();
    Filter<ClassInfo> policyFilter = safeFilter;
    Filter<ClassInfo> scanFilter = safeFilter;

    // 2. Configuration Flags
    String checkedClasses = System.getProperty("runtime.classes");
    boolean isGlobalMode = Boolean.getBoolean("runtime.global");
    boolean trustAnnotatedFor = Boolean.getBoolean("runtime.trustAnnotatedFor");

    if (checkedClasses != null && !checkedClasses.isBlank()) {
      System.out.println("[RuntimeAgent] Checked Scope restricted to: " + checkedClasses);
      Filter<ClassInfo> listFilter = new ClassListFilter(Arrays.asList(checkedClasses.split(",")));

      // Policy: Must be Safe AND in the Checked List
      policyFilter = info -> safeFilter.test(info) && listFilter.test(info);

      if (trustAnnotatedFor) {
        System.out.println(
            "[RuntimeAgent] Auto-Discovery Enabled. Scanning all safe classes for annotations.");
        scanFilter = safeFilter;
      } else {
        scanFilter = policyFilter;
      }
    }

    if (isGlobalMode) {
      System.out.println("[RuntimeAgent] Global Mode ENABLED. Scanning all safe classes.");
      scanFilter = safeFilter;
    }

    // 3. Load Checker
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

    // 4. Register with new flag
    inst.addTransformer(
        new RuntimeTransformer(scanFilter, policyFilter, checker, trustAnnotatedFor), false);
  }
}
