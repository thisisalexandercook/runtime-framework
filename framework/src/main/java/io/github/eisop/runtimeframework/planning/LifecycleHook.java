package io.github.eisop.runtimeframework.planning;

/** Generic lifecycle hook kinds reserved for initialization-aware enforcement. */
public enum LifecycleHook {
  CONSTRUCTOR_ENTER,
  CONSTRUCTOR_COMMIT,
  BOUNDARY_RECEIVER_USE
}
