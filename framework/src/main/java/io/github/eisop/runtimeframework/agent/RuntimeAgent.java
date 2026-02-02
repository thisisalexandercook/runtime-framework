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
    Filter<ClassInfo> safeFilter = new FrameworkSafetyFilter();
    Filter<ClassInfo> strategyFilter = safeFilter;

    String checkedClasses = System.getProperty("runtime.classes");
    boolean isGlobalMode = Boolean.getBoolean("runtime.global");
    boolean trustAnnotatedFor = Boolean.getBoolean("runtime.trustAnnotatedFor");

    if (checkedClasses != null && !checkedClasses.isBlank()) {
      System.out.println("[RuntimeAgent] Checked Scope restricted to: " + checkedClasses);
      Filter<ClassInfo> listFilter = new ClassListFilter(Arrays.asList(checkedClasses.split(",")));
      strategyFilter = info -> safeFilter.test(info) && listFilter.test(info);
    } else if (trustAnnotatedFor) {
      strategyFilter = info -> false;
    }

    Filter<ClassInfo> scanFilter = strategyFilter;
    boolean scanAll = false;

    if (trustAnnotatedFor) {
      System.out.println(
          "[RuntimeAgent] Auto-Discovery Enabled. Scanning all safe classes for annotations.");
      scanAll = true;
    }

    if (isGlobalMode) {
      System.out.println(
          "[RuntimeAgent] Global Mode ENABLED. Scanning all safe classes for external writes.");
      scanAll = true;
    }

    if (checkedClasses == null && !trustAnnotatedFor) {
      scanAll = true;
    }

    if (scanAll) {
      scanFilter = safeFilter;
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

    inst.addTransformer(
        new RuntimeTransformer(
            scanFilter, strategyFilter, checker, trustAnnotatedFor, isGlobalMode),
        false);
  }
}
