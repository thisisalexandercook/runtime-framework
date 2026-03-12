package io.github.eisop.runtimeframework.planning;

/** Describes where an instrumentation action should be emitted within a method body. */
public record InjectionPoint(Kind kind, int bytecodeIndex) {

  public InjectionPoint {
    if (kind == null) {
      throw new IllegalArgumentException("kind cannot be null");
    }
  }

  public static InjectionPoint methodEntry() {
    return new InjectionPoint(Kind.METHOD_ENTRY, -1);
  }

  public static InjectionPoint beforeInstruction(int bytecodeIndex) {
    return new InjectionPoint(Kind.BEFORE_INSTRUCTION, bytecodeIndex);
  }

  public static InjectionPoint afterInstruction(int bytecodeIndex) {
    return new InjectionPoint(Kind.AFTER_INSTRUCTION, bytecodeIndex);
  }

  public static InjectionPoint normalReturn(int bytecodeIndex) {
    return new InjectionPoint(Kind.NORMAL_RETURN, bytecodeIndex);
  }

  public static InjectionPoint bridgeEntry() {
    return new InjectionPoint(Kind.BRIDGE_ENTRY, -1);
  }

  public static InjectionPoint bridgeExit() {
    return new InjectionPoint(Kind.BRIDGE_EXIT, -1);
  }

  public enum Kind {
    METHOD_ENTRY,
    BEFORE_INSTRUCTION,
    AFTER_INSTRUCTION,
    NORMAL_RETURN,
    BRIDGE_ENTRY,
    BRIDGE_EXIT,
    CLASS_END
  }
}
