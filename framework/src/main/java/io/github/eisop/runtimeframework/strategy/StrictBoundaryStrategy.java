package io.github.eisop.runtimeframework.strategy;

import io.github.eisop.runtimeframework.core.TypeSystemConfiguration;
import io.github.eisop.runtimeframework.policy.RuntimePolicy;

/**
 * Backward-compatible alias for boundary strategy behavior.
 *
 * <p>Global behavior is now policy-driven and implemented by {@link BoundaryStrategy}.
 */
public class StrictBoundaryStrategy extends BoundaryStrategy {

  public StrictBoundaryStrategy(TypeSystemConfiguration configuration, RuntimePolicy policy) {
    super(configuration, policy);
  }
}
