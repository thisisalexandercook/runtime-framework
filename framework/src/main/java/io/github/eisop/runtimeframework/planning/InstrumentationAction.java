package io.github.eisop.runtimeframework.planning;

import io.github.eisop.runtimeframework.contracts.ValueContract;
import io.github.eisop.runtimeframework.runtime.AttributionKind;
import java.util.Objects;

/** A concrete action emitted by the planner for later bytecode instrumentation. */
public sealed interface InstrumentationAction
    permits InstrumentationAction.ValueCheckAction, InstrumentationAction.LifecycleHookAction {

  InjectionPoint injectionPoint();

  record ValueCheckAction(
      InjectionPoint injectionPoint,
      ValueAccess valueAccess,
      ValueContract contract,
      AttributionKind attribution,
      DiagnosticSpec diagnostic)
      implements InstrumentationAction {
    public ValueCheckAction {
      Objects.requireNonNull(injectionPoint, "injectionPoint");
      Objects.requireNonNull(valueAccess, "valueAccess");
      Objects.requireNonNull(contract, "contract");
      Objects.requireNonNull(attribution, "attribution");
      Objects.requireNonNull(diagnostic, "diagnostic");
    }
  }

  record LifecycleHookAction(
      InjectionPoint injectionPoint, ValueAccess valueAccess, LifecycleHook hook)
      implements InstrumentationAction {
    public LifecycleHookAction {
      Objects.requireNonNull(injectionPoint, "injectionPoint");
      Objects.requireNonNull(valueAccess, "valueAccess");
      Objects.requireNonNull(hook, "hook");
    }
  }
}
