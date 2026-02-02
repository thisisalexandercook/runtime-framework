package io.github.eisop.runtimeframework.core;

/** Defines the type of validation logic to apply for a specific annotation. */
public enum ValidationKind {
  /**
   * The qualifier requires runtime verification. The associated {@link CheckGenerator} will be
   * invoked to generate the check logic.
   */
  ENFORCE,

  /**
   * The qualifier explicitly indicates that no check is required. (e.g., a Top type like @Nullable,
   * or an explicit suppression).
   */
  NOOP
}
