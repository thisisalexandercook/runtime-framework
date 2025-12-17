package io.github.eisop.runtimeframework.checker.nullness;

import io.github.eisop.runtimeframework.core.AnnotationInstrumenter;
import io.github.eisop.runtimeframework.core.RuntimeChecker;
import io.github.eisop.runtimeframework.core.RuntimeInstrumenter;
import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.Filter;
import io.github.eisop.runtimeframework.policy.EnforcementPolicy;
import io.github.eisop.runtimeframework.resolution.HierarchyResolver;
import io.github.eisop.runtimeframework.resolution.ReflectionHierarchyResolver;
import java.util.List;

public class NullnessRuntimeChecker extends RuntimeChecker {

  @Override
  public String getName() {
    return "nullness";
  }

  @Override
  public RuntimeInstrumenter getInstrumenter(Filter<ClassInfo> filter) {
    // 1. Create Policy
    EnforcementPolicy policy = createPolicy(List.of(new NonNullTarget()), filter);

    // 2. Create Resolver
    HierarchyResolver resolver =
        new ReflectionHierarchyResolver(
            className -> filter.test(new ClassInfo(className.replace('.', '/'), null, null)));

    // 3. Create Instrumenter
    return new AnnotationInstrumenter(policy, resolver, filter);
  }
}
