package io.github.eisop.runtimeframework.core;

import io.github.eisop.runtimeframework.instrumentation.RuntimeInstrumenter;
import io.github.eisop.runtimeframework.policy.RuntimePolicy;
import io.github.eisop.runtimeframework.resolution.ResolutionEnvironment;
import io.github.eisop.runtimeframework.strategy.BoundaryStrategy;
import io.github.eisop.runtimeframework.strategy.InstrumentationStrategy;

/**
 * Represents a specific type system or check to be enforced (e.g., Nullness, Immutability). This
 * class acts as the configuration and factory for the instrumentation logic.
 */
public abstract class RuntimeChecker {

  /** Returns the name of this checker. This string should match the name used in AnnotatedFor */
  public abstract String getName();

  /**
   * Creates or returns the instrumenter that injects this checker's logic.
   *
   * @param policy The active runtime policy that classifies checked/unchecked scope.
   */
  public abstract RuntimeInstrumenter getInstrumenter(RuntimePolicy policy);

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
