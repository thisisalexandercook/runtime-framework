package io.github.eisop.runtimeframework.agent;

import io.github.eisop.runtimeframework.core.RuntimeChecker;
import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.ClassListFilter;
import io.github.eisop.runtimeframework.filter.Filter;
import io.github.eisop.runtimeframework.filter.FrameworkSafetyFilter;
import io.github.eisop.runtimeframework.policy.DefaultRuntimePolicy;
import io.github.eisop.runtimeframework.policy.RuntimePolicy;
import io.github.eisop.runtimeframework.runtime.RuntimeVerifier;
import io.github.eisop.runtimeframework.runtime.ViolationHandler;
import java.lang.instrument.Instrumentation;
import java.util.Arrays;

public final class RuntimeAgent {

  public static void premain(String args, Instrumentation inst) {
    Filter<ClassInfo> safeFilter = new FrameworkSafetyFilter();

    String checkedClasses = System.getProperty("runtime.classes");
    boolean isGlobalMode = Boolean.getBoolean("runtime.global");
    boolean trustAnnotatedFor = Boolean.getBoolean("runtime.trustAnnotatedFor");
    Filter<ClassInfo> checkedScopeFilter =
        (checkedClasses != null && !checkedClasses.isBlank())
            ? new ClassListFilter(Arrays.asList(checkedClasses.split(",")))
            : Filter.rejectAll();

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
            "runtime.checker",
            "io.github.eisop.runtimeframework.checker.nullness.NullnessRuntimeChecker");

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

    RuntimePolicy policy =
        new DefaultRuntimePolicy(
            safeFilter, checkedScopeFilter, isGlobalMode, trustAnnotatedFor, checker.getName());

    System.out.println("[RuntimeAgent] Policy mode: " + (isGlobalMode ? "GLOBAL" : "STANDARD"));
    if (checkedClasses != null && !checkedClasses.isBlank()) {
      System.out.println("[RuntimeAgent] Checked scope list: " + checkedClasses);
    }
    if (trustAnnotatedFor) {
      System.out.println("[RuntimeAgent] Checked scope includes @AnnotatedFor classes.");
    }

    inst.addTransformer(new RuntimeTransformer(policy, checker), false);
  }
}
