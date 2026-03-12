package io.github.eisop.runtimeframework.planning;

import java.util.Objects;

/** A semantic value-flow event recognized while scanning bytecode. */
public sealed interface FlowEvent
    permits FlowEvent.MethodParameter,
        FlowEvent.MethodReturn,
        FlowEvent.BoundaryCallReturn,
        FlowEvent.FieldRead,
        FlowEvent.FieldWrite,
        FlowEvent.ArrayLoad,
        FlowEvent.ArrayStore,
        FlowEvent.LocalStore,
        FlowEvent.BridgeParameter,
        FlowEvent.BridgeReturn,
        FlowEvent.OverrideParameter,
        FlowEvent.OverrideReturn,
        FlowEvent.ConstructorEnter,
        FlowEvent.ConstructorCommit,
        FlowEvent.BoundaryReceiverUse {

  FlowKind kind();

  MethodContext methodContext();

  BytecodeLocation location();

  TargetRef target();

  record MethodParameter(
      MethodContext methodContext,
      BytecodeLocation location,
      TargetRef.MethodParameter target)
      implements FlowEvent {
    public MethodParameter {
      Objects.requireNonNull(methodContext, "methodContext");
      Objects.requireNonNull(location, "location");
      Objects.requireNonNull(target, "target");
    }

    @Override
    public FlowKind kind() {
      return FlowKind.METHOD_PARAMETER;
    }
  }

  record MethodReturn(
      MethodContext methodContext, BytecodeLocation location, TargetRef.MethodReturn target)
      implements FlowEvent {
    public MethodReturn {
      Objects.requireNonNull(methodContext, "methodContext");
      Objects.requireNonNull(location, "location");
      Objects.requireNonNull(target, "target");
    }

    @Override
    public FlowKind kind() {
      return FlowKind.METHOD_RETURN;
    }
  }

  record BoundaryCallReturn(
      MethodContext methodContext, BytecodeLocation location, TargetRef.InvokedMethod target)
      implements FlowEvent {
    public BoundaryCallReturn {
      Objects.requireNonNull(methodContext, "methodContext");
      Objects.requireNonNull(location, "location");
      Objects.requireNonNull(target, "target");
    }

    @Override
    public FlowKind kind() {
      return FlowKind.BOUNDARY_CALL_RETURN;
    }
  }

  record FieldRead(MethodContext methodContext, BytecodeLocation location, TargetRef.Field target)
      implements FlowEvent {
    public FieldRead {
      Objects.requireNonNull(methodContext, "methodContext");
      Objects.requireNonNull(location, "location");
      Objects.requireNonNull(target, "target");
    }

    @Override
    public FlowKind kind() {
      return FlowKind.FIELD_READ;
    }
  }

  record FieldWrite(
      MethodContext methodContext,
      BytecodeLocation location,
      TargetRef.Field target,
      boolean isStaticAccess)
      implements FlowEvent {
    public FieldWrite {
      Objects.requireNonNull(methodContext, "methodContext");
      Objects.requireNonNull(location, "location");
      Objects.requireNonNull(target, "target");
    }

    @Override
    public FlowKind kind() {
      return FlowKind.FIELD_WRITE;
    }
  }

  record ArrayLoad(
      MethodContext methodContext,
      BytecodeLocation location,
      TargetRef.ArrayComponent target)
      implements FlowEvent {
    public ArrayLoad {
      Objects.requireNonNull(methodContext, "methodContext");
      Objects.requireNonNull(location, "location");
      Objects.requireNonNull(target, "target");
    }

    @Override
    public FlowKind kind() {
      return FlowKind.ARRAY_LOAD;
    }
  }

  record ArrayStore(
      MethodContext methodContext,
      BytecodeLocation location,
      TargetRef.ArrayComponent target)
      implements FlowEvent {
    public ArrayStore {
      Objects.requireNonNull(methodContext, "methodContext");
      Objects.requireNonNull(location, "location");
      Objects.requireNonNull(target, "target");
    }

    @Override
    public FlowKind kind() {
      return FlowKind.ARRAY_STORE;
    }
  }

  record LocalStore(MethodContext methodContext, BytecodeLocation location, TargetRef.Local target)
      implements FlowEvent {
    public LocalStore {
      Objects.requireNonNull(methodContext, "methodContext");
      Objects.requireNonNull(location, "location");
      Objects.requireNonNull(target, "target");
    }

    @Override
    public FlowKind kind() {
      return FlowKind.LOCAL_STORE;
    }
  }

  record BridgeParameter(
      MethodContext methodContext,
      BytecodeLocation location,
      TargetRef.MethodParameter target)
      implements FlowEvent {
    public BridgeParameter {
      Objects.requireNonNull(methodContext, "methodContext");
      Objects.requireNonNull(location, "location");
      Objects.requireNonNull(target, "target");
    }

    @Override
    public FlowKind kind() {
      return FlowKind.BRIDGE_PARAMETER;
    }
  }

  record BridgeReturn(
      MethodContext methodContext, BytecodeLocation location, TargetRef.MethodReturn target)
      implements FlowEvent {
    public BridgeReturn {
      Objects.requireNonNull(methodContext, "methodContext");
      Objects.requireNonNull(location, "location");
      Objects.requireNonNull(target, "target");
    }

    @Override
    public FlowKind kind() {
      return FlowKind.BRIDGE_RETURN;
    }
  }

  record OverrideParameter(
      MethodContext methodContext,
      BytecodeLocation location,
      TargetRef.MethodParameter target)
      implements FlowEvent {
    public OverrideParameter {
      Objects.requireNonNull(methodContext, "methodContext");
      Objects.requireNonNull(location, "location");
      Objects.requireNonNull(target, "target");
    }

    @Override
    public FlowKind kind() {
      return FlowKind.OVERRIDE_PARAMETER;
    }
  }

  record OverrideReturn(
      MethodContext methodContext, BytecodeLocation location, TargetRef.MethodReturn target)
      implements FlowEvent {
    public OverrideReturn {
      Objects.requireNonNull(methodContext, "methodContext");
      Objects.requireNonNull(location, "location");
      Objects.requireNonNull(target, "target");
    }

    @Override
    public FlowKind kind() {
      return FlowKind.OVERRIDE_RETURN;
    }
  }

  record ConstructorEnter(
      MethodContext methodContext, BytecodeLocation location, TargetRef.Receiver target)
      implements FlowEvent {
    public ConstructorEnter {
      Objects.requireNonNull(methodContext, "methodContext");
      Objects.requireNonNull(location, "location");
      Objects.requireNonNull(target, "target");
    }

    @Override
    public FlowKind kind() {
      return FlowKind.CONSTRUCTOR_ENTER;
    }
  }

  record ConstructorCommit(
      MethodContext methodContext, BytecodeLocation location, TargetRef.Receiver target)
      implements FlowEvent {
    public ConstructorCommit {
      Objects.requireNonNull(methodContext, "methodContext");
      Objects.requireNonNull(location, "location");
      Objects.requireNonNull(target, "target");
    }

    @Override
    public FlowKind kind() {
      return FlowKind.CONSTRUCTOR_COMMIT;
    }
  }

  record BoundaryReceiverUse(
      MethodContext methodContext, BytecodeLocation location, TargetRef.Receiver target)
      implements FlowEvent {
    public BoundaryReceiverUse {
      Objects.requireNonNull(methodContext, "methodContext");
      Objects.requireNonNull(location, "location");
      Objects.requireNonNull(target, "target");
    }

    @Override
    public FlowKind kind() {
      return FlowKind.BOUNDARY_RECEIVER_USE;
    }
  }
}
