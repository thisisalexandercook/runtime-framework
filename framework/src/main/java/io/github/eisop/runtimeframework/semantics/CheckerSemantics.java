package io.github.eisop.runtimeframework.semantics;

/** Describes checker-owned runtime semantics independent of bytecode rewriting details. */
public interface CheckerSemantics {

  ContractResolver contracts();

  PropertyEmitter emitter();

  default LifecycleSemantics lifecycle() {
    return LifecycleSemantics.none();
  }
}
