package io.github.eisop.runtimeframework.agent;

import io.github.eisop.runtimeframework.core.RuntimeChecker;
import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.ClassListFilter;
import io.github.eisop.runtimeframework.filter.Filter;
import io.github.eisop.runtimeframework.filter.FrameworkSafetyFilter;
import io.github.eisop.runtimeframework.runtime.RuntimeVerifier;
import io.github.eisop.runtimeframework.runtime.ViolationHandler;
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

    // Logic for Policy Filter (Who is Checked?)
    if (checkedClasses != null && !checkedClasses.isBlank()) {
      System.out.println("[RuntimeAgent] Checked Scope restricted to: " + checkedClasses);
      Filter<ClassInfo> listFilter = new ClassListFilter(Arrays.asList(checkedClasses.split(",")));
      policyFilter = info -> safeFilter.test(info) && listFilter.test(info);
    } else if (trustAnnotatedFor) {
      // New logic: If relying on annotations and no list is provided, default to Empty Set.
      // This allows @AnnotatedFor to be the sole mechanism for opting in classes as Checked.
      policyFilter = info -> false;
    }

    // Logic for Scan Filter (Who do we parse?)
    if (trustAnnotatedFor) {
      System.out.println(
          "[RuntimeAgent] Auto-Discovery Enabled. Scanning all safe classes for annotations.");
      scanFilter = safeFilter;
    } else if (isGlobalMode) {
      System.out.println("[RuntimeAgent] Global Mode ENABLED. Scanning all safe classes.");
      scanFilter = safeFilter;
    } else if (checkedClasses != null) {
      // Optimization: Only scan what is explicitly checked
      scanFilter = policyFilter;
    }

    // 3. Configure Violation Handler
    String handlerClassName = System.getProperty("runtime.handler");
    if (handlerClassName != null && !handlerClassName.isBlank()) {
      try {
        System.out.println("[RuntimeAgent] Setting ViolationHandler: " + handlerClassName);
        Class<?> handlerClass = Class.forName(handlerClassName);
        ViolationHandler handler = (ViolationHandler) handlerClass.getConstructor().newInstance();
        RuntimeVerifier.setViolationHandler(handler);
      } catch (Exception e) {
        System.err.println(
            "[RuntimeAgent] ERROR: Could not instantiate handler: " + handlerClassName);
        e.printStackTrace();
      }
    }

    // 4. Load Checker
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

    // 5. Register
    inst.addTransformer(
        new RuntimeTransformer(scanFilter, policyFilter, checker, trustAnnotatedFor), false);
  }
}
