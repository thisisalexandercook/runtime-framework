package io.github.eisop.runtimeframework.core;

public abstract class RuntimeChecker {

  /** Returns the human-readable name of this checker */
  public abstract String getName();

  /** Creates or returns the instrumenter that injects this checker's logic. */
  public abstract RuntimeInstrumenter getInstrumenter();
}
