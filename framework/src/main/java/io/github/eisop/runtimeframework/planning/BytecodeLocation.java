package io.github.eisop.runtimeframework.planning;

/** Identifies a source and bytecode position within a method. */
public record BytecodeLocation(int bytecodeIndex, int sourceLine) {

  public static final int UNKNOWN_LINE = -1;

  public static BytecodeLocation at(int bytecodeIndex, int sourceLine) {
    return new BytecodeLocation(bytecodeIndex, sourceLine);
  }

  public static BytecodeLocation unknown() {
    return new BytecodeLocation(-1, UNKNOWN_LINE);
  }

  public boolean hasSourceLine() {
    return sourceLine != UNKNOWN_LINE;
  }
}
