package io.github.eisop.runtimeframework.planning;

/** Describes how emitted code should access the value being checked. */
public sealed interface ValueAccess
    permits ValueAccess.OperandStack, ValueAccess.LocalSlot, ValueAccess.ThisReference {

  record OperandStack(int depthFromTop) implements ValueAccess {}

  record LocalSlot(int slot) implements ValueAccess {}

  record ThisReference() implements ValueAccess {}
}
