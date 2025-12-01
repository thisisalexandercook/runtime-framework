package io.github.eisop.runtimeframework.core;

import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.Filter;

/**
 * Represents a specific type system or check to be enforced (e.g., Nullness, Immutability). This
 * class acts as the configuration and factory for the instrumentation logic.
 */
public abstract class RuntimeChecker {

  /** Returns the human-readable name of this checker (e.g., "Nullness Runtime Checker"). */
  public abstract String getName();

  /**
   * Creates or returns the instrumenter that injects this checker's logic.
   *
   * @param filter The safety filter currently active in the Agent. The instrumenter can use this to
   *     determine boundary checks (Checked vs Unchecked).
   */
  public abstract RuntimeInstrumenter getInstrumenter(Filter<ClassInfo> filter);
}
