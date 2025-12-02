package io.github.eisop.runtimeframework.checker.nullness;

import io.github.eisop.runtimeframework.core.AnnotationInstrumenter;
import io.github.eisop.runtimeframework.core.EnforcementPolicy;
import io.github.eisop.runtimeframework.core.HierarchyResolver;
import io.github.eisop.runtimeframework.core.ReflectionHierarchyResolver;
import io.github.eisop.runtimeframework.core.RuntimeChecker;
import io.github.eisop.runtimeframework.core.RuntimeInstrumenter;
import io.github.eisop.runtimeframework.core.StandardEnforcementPolicy;
import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.Filter;
import java.util.List;

public class NullnessRuntimeChecker extends RuntimeChecker {

  @Override
  public String getName() {
    return "Nullness Runtime Checker";
  }

  @Override
  public RuntimeInstrumenter getInstrumenter(Filter<ClassInfo> filter) {
    // 1. Define Policy (Auto-discovers Default Target from @DefaultQualifierInHierarchy)
    EnforcementPolicy policy = new StandardEnforcementPolicy(List.of(new NonNullTarget()), filter);

    // 2. Define Hierarchy Resolver
    HierarchyResolver resolver =
        new ReflectionHierarchyResolver(
            className -> filter.test(new ClassInfo(className.replace('.', '/'), null, null)));

    // 3. Create Instrumenter
    return new AnnotationInstrumenter(policy, resolver);
  }
}
