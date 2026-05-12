package io.github.eisop.runtimeframework.agent;

import io.github.eisop.runtimeframework.config.RuntimeOptions;
import io.github.eisop.runtimeframework.core.RuntimeChecker;
import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.ClassListFilter;
import io.github.eisop.runtimeframework.filter.Filter;
import io.github.eisop.runtimeframework.filter.FrameworkSafetyFilter;
import io.github.eisop.runtimeframework.policy.RuntimePolicy;
import io.github.eisop.runtimeframework.policy.ScopeAwareRuntimePolicy;
import io.github.eisop.runtimeframework.resolution.ResolutionEnvironment;
import io.github.eisop.runtimeframework.runtime.RuntimeVerifier;
import io.github.eisop.runtimeframework.runtime.ViolationHandler;
import java.lang.instrument.Instrumentation;
import java.util.Arrays;

public final class RuntimeAgent {

  public static void premain(String args, Instrumentation inst) {
    RuntimeOptions options = RuntimeOptions.fromSystemProperties();
    Filter<ClassInfo> safeFilter = new FrameworkSafetyFilter();

    Filter<ClassInfo> checkedScopeFilter =
        options.hasCheckedClasses()
            ? new ClassListFilter(Arrays.asList(options.checkedClasses().split(",")))
            : Filter.rejectAll();

    // Configure ViolationHandler before instrumented checks can run.
    if (options.hasHandlerClassName()) {
      try {
        System.out.println(
            "[RuntimeAgent] Setting ViolationHandler: " + options.handlerClassName());
        Class<?> handlerClass = Class.forName(options.handlerClassName());
        ViolationHandler handler = (ViolationHandler) handlerClass.getConstructor().newInstance();
        RuntimeVerifier.setViolationHandler(handler);
      } catch (Exception e) {
        System.err.println(
            "[RuntimeAgent] ERROR: Could not instantiate handler: " + options.handlerClassName());
        e.printStackTrace();
      }
    }

    RuntimeChecker checker;
    try {
      System.out.println("[RuntimeAgent] Loading checker: " + options.checkerClassName());
      Class<?> clazz = Class.forName(options.checkerClassName());
      checker = (RuntimeChecker) clazz.getConstructor().newInstance();
    } catch (Exception e) {
      System.err.println(
          "[RuntimeAgent] FATAL: Could not instantiate checker: " + options.checkerClassName());
      e.printStackTrace();
      return;
    }

    RuntimePolicy policy =
        new ScopeAwareRuntimePolicy(
            safeFilter,
            checkedScopeFilter,
            options.globalMode(),
            options.trustAnnotatedFor(),
            checker.getName(),
            ResolutionEnvironment.system());

    System.out.println(
        "[RuntimeAgent] Policy mode: " + (options.globalMode() ? "GLOBAL" : "STANDARD"));
    if (options.hasCheckedClasses()) {
      System.out.println("[RuntimeAgent] Checked scope list: " + options.checkedClasses());
    }
    if (options.trustAnnotatedFor()) {
      System.out.println("[RuntimeAgent] Checked scope includes @AnnotatedFor classes.");
    }

    inst.addTransformer(new RuntimeTransformer(policy, checker, options), false);
  }
}
