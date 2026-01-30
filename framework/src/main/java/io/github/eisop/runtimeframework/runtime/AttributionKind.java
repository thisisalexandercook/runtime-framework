package io.github.eisop.runtimeframework.runtime;

/** Defines how a violation should be attributed in the stack trace. */
public enum AttributionKind {
  /**
   * The violation occurred in the current method.
   *
   * <p>Example: A method returns null but promised @NonNull. The blame is on the method itself.
   */
  LOCAL,

  /**
   * The violation occurred at the call site of the current method.
   *
   * <p>Example: A method received null as an argument but requires @NonNull. The blame is on the
   * caller.
   */
  CALLER
}
