package io.github.eisop.runtimeframework.semantics;

/** Placeholder for lifecycle-aware semantics such as initialization overlays. */
public interface LifecycleSemantics {

  static LifecycleSemantics none() {
    return NoneLifecycleSemantics.INSTANCE;
  }

  enum NoneLifecycleSemantics implements LifecycleSemantics {
    INSTANCE
  }
}
