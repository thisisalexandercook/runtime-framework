package io.github.eisop.runtimeframework.planning;

import io.github.eisop.runtimeframework.resolution.ParentMethod;
import java.util.List;
import java.util.Objects;

/** A planner result for a generated bridge method. */
public record BridgePlan(ParentMethod parentMethod, List<InstrumentationAction> actions) {

  public BridgePlan {
    Objects.requireNonNull(parentMethod, "parentMethod");
    Objects.requireNonNull(actions, "actions");
    actions = List.copyOf(actions);
  }

  public boolean isEmpty() {
    return actions.isEmpty();
  }
}
