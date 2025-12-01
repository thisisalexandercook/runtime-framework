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
    // 1. Base Safety Filter (Always active to protect JDK/Agent classes)
    Filter<ClassInfo> activeFilter = new FrameworkSafetyFilter();

    // 2. Optional Checked List (Controlled by -Druntime.classes=com.Foo,com.Bar)
    // Renamed from 'allowedClasses' to 'checkedClasses' to match the concept of Checked Code.
    String checkedClasses = System.getProperty("runtime.classes");
    if (checkedClasses != null && !checkedClasses.isBlank()) {
      System.out.println("[RuntimeAgent] Restricting instrumentation to: " + checkedClasses);

      Filter<ClassInfo> checkedList = new ClassListFilter(Arrays.asList(checkedClasses.split(",")));

      // Composition: Must be Safe AND in the Checked List
      Filter<ClassInfo> safety = activeFilter;
      activeFilter = info -> safety.test(info) && checkedList.test(info);
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

    // 4. Register
    inst.addTransformer(new RuntimeTransformer(activeFilter, checker), false);
  }
}
