package io.github.eisop.runtimeframework.planning;

import io.github.eisop.runtimeframework.contracts.ValueContract;
import io.github.eisop.runtimeframework.core.CheckGenerator;
import io.github.eisop.runtimeframework.runtime.AttributionKind;
import java.lang.classfile.TypeKind;
import java.util.Objects;

/** A concrete action emitted by the planner for later bytecode instrumentation. */
public sealed interface InstrumentationAction
    permits InstrumentationAction.ValueCheckAction,
        InstrumentationAction.LegacyCheckAction,
        InstrumentationAction.LifecycleHookAction {

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

  /**
   * Transitional action used while the planner is still backed by the legacy strategy/check
   * generator pipeline.
   */
  record LegacyCheckAction(
      InjectionPoint injectionPoint,
      ValueAccess valueAccess,
      TypeKind valueType,
      CheckGenerator generator,
      DiagnosticSpec diagnostic)
      implements InstrumentationAction {
    public LegacyCheckAction {
      Objects.requireNonNull(injectionPoint, "injectionPoint");
      Objects.requireNonNull(valueAccess, "valueAccess");
      Objects.requireNonNull(valueType, "valueType");
      Objects.requireNonNull(generator, "generator");
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
