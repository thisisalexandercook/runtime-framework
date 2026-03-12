package io.github.eisop.runtimeframework.planning;

import java.util.List;
import java.util.Objects;

/** A planner result for one method body. */
public record MethodPlan(List<InstrumentationAction> actions) {

  public MethodPlan {
    Objects.requireNonNull(actions, "actions");
    actions = List.copyOf(actions);
  }

  public static MethodPlan empty() {
    return new MethodPlan(List.of());
  }

  public boolean isEmpty() {
    return actions.isEmpty();
  }
}
