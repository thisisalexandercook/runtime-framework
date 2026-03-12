package io.github.eisop.runtimeframework.planning;

/** Semantic kinds of value flow recognized by the runtime framework planner. */
public enum FlowKind {
  METHOD_PARAMETER,
  METHOD_RETURN,
  BOUNDARY_CALL_RETURN,
  FIELD_READ,
  FIELD_WRITE,
  ARRAY_LOAD,
  ARRAY_STORE,
  LOCAL_STORE,
  BRIDGE_PARAMETER,
  BRIDGE_RETURN,
  OVERRIDE_PARAMETER,
  OVERRIDE_RETURN,
  CONSTRUCTOR_ENTER,
  CONSTRUCTOR_COMMIT,
  BOUNDARY_RECEIVER_USE
}
