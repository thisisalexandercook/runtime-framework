package io.github.eisop.runtimeframework.planning;

import io.github.eisop.runtimeframework.resolution.ParentMethod;
import java.util.List;

/** Produces instrumentation plans for method flows and generated bridges. */
public interface EnforcementPlanner {

  MethodPlan planMethod(MethodContext methodContext, List<? extends FlowEvent> events);

  default MethodPlan planMethod(MethodContext methodContext, FlowEvent... events) {
    return planMethod(methodContext, List.of(events));
  }

  MethodPlan planUncheckedReceiverFallbackReturn(
      MethodContext methodContext, BytecodeLocation location, TargetRef.InvokedMethod target);

  boolean shouldGenerateBridge(ClassContext classContext, ParentMethod parentMethod);

  BridgePlan planBridge(ClassContext classContext, ParentMethod parentMethod);
}
