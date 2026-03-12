package io.github.eisop.runtimeframework.core;

import io.github.eisop.runtimeframework.instrumentation.EnforcementInstrumenter;
import io.github.eisop.runtimeframework.instrumentation.RuntimeInstrumenter;
import io.github.eisop.runtimeframework.planning.SemanticsBackedEnforcementPlanner;
import io.github.eisop.runtimeframework.policy.RuntimePolicy;
import io.github.eisop.runtimeframework.resolution.BytecodeHierarchyResolver;
import io.github.eisop.runtimeframework.resolution.HierarchyResolver;
import io.github.eisop.runtimeframework.resolution.ResolutionEnvironment;
import io.github.eisop.runtimeframework.semantics.CheckerSemantics;
import io.github.eisop.runtimeframework.strategy.BoundaryStrategy;
import io.github.eisop.runtimeframework.strategy.InstrumentationStrategy;

/**
 * Represents a specific type system or check to be enforced (e.g., Nullness, Immutability). This
 * class acts as the configuration and factory for the instrumentation logic.
 */
public abstract class RuntimeChecker {

  /** Returns the name of this checker. This string should match the name used in AnnotatedFor */
  public abstract String getName();

  /** Returns the semantic model used by the framework planner for this checker. */
  public abstract CheckerSemantics getSemantics();

  public final RuntimeInstrumenter createInstrumenter(RuntimePolicy policy) {
    return createInstrumenter(policy, ResolutionEnvironment.system());
  }

  public RuntimeInstrumenter createInstrumenter(
      RuntimePolicy policy, ResolutionEnvironment resolutionEnvironment) {
    CheckerSemantics semantics = getSemantics();
    HierarchyResolver resolver =
        new BytecodeHierarchyResolver(info -> policy.isChecked(info), resolutionEnvironment);
    return new EnforcementInstrumenter(
        new SemanticsBackedEnforcementPlanner(policy, semantics, resolutionEnvironment),
        resolver,
        semantics.emitter());
  }

  /**
   * Transitional compatibility hook retained while external callers migrate to framework-owned
   * instrumenter construction.
   */
  @Deprecated
  public RuntimeInstrumenter getInstrumenter(RuntimePolicy policy) {
    return createInstrumenter(policy);
  }

  /**
   * Helper method to create the instrumentation strategy based on the active policy.
   *
   * @param config The TypeSystemConfiguration for this checker.
   * @param policy The active runtime policy.
   * @return A configured InstrumentationStrategy.
   */
  protected InstrumentationStrategy createStrategy(
      TypeSystemConfiguration config, RuntimePolicy policy) {
    return createStrategy(config, policy, ResolutionEnvironment.system());
  }

  protected InstrumentationStrategy createStrategy(
      TypeSystemConfiguration config,
      RuntimePolicy policy,
      ResolutionEnvironment resolutionEnvironment) {
    return new BoundaryStrategy(config, policy, resolutionEnvironment);
  }
}
