package io.github.eisop.runtimeframework.core;

import io.github.eisop.runtimeframework.config.RuntimeOptions;
import io.github.eisop.runtimeframework.instrumentation.EnforcementInstrumenter;
import io.github.eisop.runtimeframework.instrumentation.RuntimeInstrumenter;
import io.github.eisop.runtimeframework.planning.ContractEnforcementPlanner;
import io.github.eisop.runtimeframework.policy.RuntimePolicy;
import io.github.eisop.runtimeframework.resolution.BytecodeHierarchyResolver;
import io.github.eisop.runtimeframework.resolution.HierarchyResolver;
import io.github.eisop.runtimeframework.resolution.ResolutionEnvironment;
import io.github.eisop.runtimeframework.semantics.CheckerSemantics;

/**
 * Represents a specific type system or check to be enforced (e.g., Nullness, Immutability). This
 * class acts as the configuration and factory for the instrumentation logic.
 */
public abstract class RuntimeChecker {

  /** Returns the name of this checker. This string should match the name used in AnnotatedFor */
  public abstract String getName();

  /** Returns the semantic model used by the framework planner for this checker. */
  public abstract CheckerSemantics getSemantics();

  /** Returns the semantic model used by the framework planner for this checker and option set. */
  public CheckerSemantics getSemantics(RuntimeOptions options) {
    return getSemantics();
  }

  public final RuntimeInstrumenter createInstrumenter(RuntimePolicy policy) {
    return createInstrumenter(policy, RuntimeOptions.fromSystemProperties());
  }

  public final RuntimeInstrumenter createInstrumenter(
      RuntimePolicy policy, RuntimeOptions options) {
    return createInstrumenter(policy, ResolutionEnvironment.system(), options);
  }

  public RuntimeInstrumenter createInstrumenter(
      RuntimePolicy policy, ResolutionEnvironment resolutionEnvironment) {
    return createInstrumenter(policy, resolutionEnvironment, RuntimeOptions.fromSystemProperties());
  }

  public RuntimeInstrumenter createInstrumenter(
      RuntimePolicy policy, ResolutionEnvironment resolutionEnvironment, RuntimeOptions options) {
    CheckerSemantics semantics = getSemantics(options);
    HierarchyResolver resolver =
        new BytecodeHierarchyResolver(info -> policy.isChecked(info), resolutionEnvironment);
    return new EnforcementInstrumenter(
        new ContractEnforcementPlanner(policy, semantics, resolutionEnvironment),
        resolver,
        semantics.emitter(),
        policy,
        resolutionEnvironment,
        options);
  }

  /**
   * Transitional compatibility hook retained while external callers migrate to framework-owned
   * instrumenter construction.
   */
  @Deprecated
  public RuntimeInstrumenter getInstrumenter(RuntimePolicy policy) {
    return createInstrumenter(policy);
  }
}
