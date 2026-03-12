package io.github.eisop.runtimeframework.policy;

import io.github.eisop.runtimeframework.filter.AnnotatedForFilter;
import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.Filter;
import io.github.eisop.runtimeframework.resolution.ResolutionEnvironment;
import java.lang.classfile.ClassModel;

/** Default policy implementation for checked-scope and global-mode behavior. */
public final class DefaultRuntimePolicy implements RuntimePolicy {

  private final Filter<ClassInfo> instrumentationSafetyFilter;
  private final Filter<ClassInfo> checkedScopeFilter;
  private final boolean isGlobalMode;
  private final boolean trustAnnotatedFor;
  private final AnnotatedForFilter annotatedForFilter;

  public DefaultRuntimePolicy(
      Filter<ClassInfo> instrumentationSafetyFilter,
      Filter<ClassInfo> checkedScopeFilter,
      boolean isGlobalMode,
      boolean trustAnnotatedFor,
      String checkerName) {
    this(
        instrumentationSafetyFilter,
        checkedScopeFilter,
        isGlobalMode,
        trustAnnotatedFor,
        checkerName,
        ResolutionEnvironment.system());
  }

  public DefaultRuntimePolicy(
      Filter<ClassInfo> instrumentationSafetyFilter,
      Filter<ClassInfo> checkedScopeFilter,
      boolean isGlobalMode,
      boolean trustAnnotatedFor,
      String checkerName,
      ResolutionEnvironment resolutionEnvironment) {
    this.instrumentationSafetyFilter = instrumentationSafetyFilter;
    this.checkedScopeFilter = checkedScopeFilter;
    this.isGlobalMode = isGlobalMode;
    this.trustAnnotatedFor = trustAnnotatedFor;
    this.annotatedForFilter =
        trustAnnotatedFor ? new AnnotatedForFilter(checkerName, resolutionEnvironment) : null;
  }

  @Override
  public ClassClassification classify(ClassInfo info) {
    if (!instrumentationSafetyFilter.test(info)) {
      return ClassClassification.SKIP;
    }

    if (isExplicitlyChecked(info) || isAnnotatedForChecked(info)) {
      return ClassClassification.CHECKED;
    }

    return isGlobalMode ? ClassClassification.UNCHECKED : ClassClassification.SKIP;
  }

  @Override
  public ClassClassification classify(ClassInfo info, ClassModel model) {
    if (!instrumentationSafetyFilter.test(info)) {
      return ClassClassification.SKIP;
    }

    boolean checked = isExplicitlyChecked(info);
    if (!checked && trustAnnotatedFor && annotatedForFilter != null) {
      checked = annotatedForFilter.test(model, info.loader());
    }

    if (checked) {
      return ClassClassification.CHECKED;
    }

    return isGlobalMode ? ClassClassification.UNCHECKED : ClassClassification.SKIP;
  }

  @Override
  public boolean isGlobalMode() {
    return isGlobalMode;
  }

  private boolean isExplicitlyChecked(ClassInfo info) {
    return checkedScopeFilter != null && checkedScopeFilter.test(info);
  }

  private boolean isAnnotatedForChecked(ClassInfo info) {
    return trustAnnotatedFor && annotatedForFilter != null && annotatedForFilter.test(info);
  }
}
