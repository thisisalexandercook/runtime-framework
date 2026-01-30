package io.github.eisop.runtimeframework.runtime;

/** Defines the strategy for handling runtime verification violations. */
public interface ViolationHandler {

  /**
   * Handle a reported violation.
   *
   * @param checkerName The name of the checker that detected the violation
   * @param message The descriptive error message provided by the verification logic
   */
  default void handleViolation(String checkerName, String message) {
    handleViolation(checkerName, message, AttributionKind.LOCAL);
  }

  /**
   * Handle a reported violation with specific attribution logic.
   *
   * @param checkerName The name of the checker that detected the violation
   * @param message The descriptive error message provided by the verification logic
   * @param attribution The strategy for determining the source of the error
   */
  void handleViolation(String checkerName, String message, AttributionKind attribution);
}
