package io.github.eisop.runtimeframework.policy;

import io.github.eisop.runtimeframework.filter.AnnotatedForFilter;
import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.Filter;
import io.github.eisop.runtimeframework.planning.FlowEvent;
import io.github.eisop.runtimeframework.resolution.ResolutionEnvironment;
import java.lang.classfile.ClassModel;

/** Runtime policy implementation for checked-scope and global-mode behavior. */
public final class ScopeAwareRuntimePolicy implements RuntimePolicy {

  private final Filter<ClassInfo> instrumentationSafetyFilter;
  private final Filter<ClassInfo> checkedScopeFilter;
  private final boolean isGlobalMode;
  private final boolean trustAnnotatedFor;
  private final AnnotatedForFilter annotatedForFilter;

  public ScopeAwareRuntimePolicy(
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

  public ScopeAwareRuntimePolicy(
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

  @Override
  public boolean allows(FlowEvent event) {
    return switch (event) {
      case FlowEvent.MethodParameter ignored -> isCheckedEnclosingMethod(event);
      case FlowEvent.MethodReturn ignored -> isCheckedEnclosingMethod(event);
      case FlowEvent.BoundaryCallReturn boundaryCallReturn ->
          isCheckedEnclosingMethod(event)
              && isUncheckedTarget(boundaryCallReturn.target().ownerInternalName(), event);
      case FlowEvent.FieldRead fieldRead ->
          isCheckedEnclosingMethod(event)
              && (isSameOwner(fieldRead.target().ownerInternalName(), event)
                  || isUncheckedTarget(fieldRead.target().ownerInternalName(), event));
      case FlowEvent.FieldWrite fieldWrite ->
          isGlobalMode
              && !isSameOwner(fieldWrite.target().ownerInternalName(), event)
              && isCheckedTarget(fieldWrite.target().ownerInternalName(), event);
      case FlowEvent.ArrayLoad ignored -> isCheckedEnclosingMethod(event);
      case FlowEvent.ArrayStore ignored -> true;
      case FlowEvent.LocalStore ignored -> isCheckedEnclosingMethod(event);
      case FlowEvent.BridgeParameter ignored -> true;
      case FlowEvent.BridgeReturn ignored -> true;
      case FlowEvent.OverrideParameter ignored -> isGlobalMode && isUncheckedEnclosingMethod(event);
      case FlowEvent.OverrideReturn ignored -> isGlobalMode && isUncheckedEnclosingMethod(event);
      case FlowEvent.ConstructorEnter ignored -> false;
      case FlowEvent.ConstructorCommit ignored -> false;
      case FlowEvent.BoundaryReceiverUse ignored -> false;
    };
  }

  private boolean isExplicitlyChecked(ClassInfo info) {
    return checkedScopeFilter != null && checkedScopeFilter.test(info);
  }

  private boolean isAnnotatedForChecked(ClassInfo info) {
    return trustAnnotatedFor && annotatedForFilter != null && annotatedForFilter.test(info);
  }

  private boolean isCheckedEnclosingMethod(FlowEvent event) {
    return event.methodContext().classContext().classification() == ClassClassification.CHECKED;
  }

  private boolean isUncheckedEnclosingMethod(FlowEvent event) {
    return event.methodContext().classContext().classification() == ClassClassification.UNCHECKED;
  }

  private boolean isSameOwner(String ownerInternalName, FlowEvent event) {
    return ownerInternalName.equals(
        event.methodContext().classContext().classInfo().internalName());
  }

  private boolean isCheckedTarget(String ownerInternalName, FlowEvent event) {
    return isChecked(
        new ClassInfo(
            ownerInternalName, event.methodContext().classContext().classInfo().loader(), null));
  }

  private boolean isUncheckedTarget(String ownerInternalName, FlowEvent event) {
    return !isCheckedTarget(ownerInternalName, event);
  }
}
