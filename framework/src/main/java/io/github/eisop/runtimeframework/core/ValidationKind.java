package io.github.eisop.runtimeframework.core;

/** Defines the semantic behavior of a qualifier in the runtime system. */
public enum ValidationKind {
  /**
   * The qualifier requires runtime verification. The associated {@link RuntimeVerifier} will be
   * invoked.
   */
  ENFORCE,

  /**
   * The qualifier explicitly indicates that no check is required. (e.g., a Top type like @Nullable,
   * or an explicit suppression).
   */
  NOOP
}
