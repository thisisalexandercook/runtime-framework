package io.github.eisop.runtimeframework.runtime;

/** Defines the strategy for handling runtime verification violations. */
public interface ViolationHandler {

  /**
   * Handle a reported violation.
   *
   * @param checkerName The name of the checker that detected the violation
   * @param message The descriptive error message provided by the verification logic
   */
  void handleViolation(String checkerName, String message);
}
