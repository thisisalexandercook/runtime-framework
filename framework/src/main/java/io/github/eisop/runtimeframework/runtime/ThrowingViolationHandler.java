package io.github.eisop.runtimeframework.runtime;

/** A violation handler that throws a RuntimeException when a check fails. */
public class ThrowingViolationHandler implements ViolationHandler {

  @Override
  public void handleViolation(String checkerName, String message) {
    throw new RuntimeException(String.format("[%s Violation] %s", checkerName, message));
  }
}
