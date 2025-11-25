package io.github.eisop.runtimeframework.runtime;

/**
 * The abstract base class for all runtime verifiers.
 *
 * <p><strong>Design Note:</strong> This class does not contain abstract methods for checks.
 * Specific verification methods (e.g., {@code checkNotNull}) must be {@code static} in subclasses
 * to allow efficient {@code invokestatic} calls from the instrumented bytecode.
 */
public abstract class RuntimeVerifier {

  private static volatile ViolationHandler handler = new ThrowingViolationHandler();

  /**
   * Configures the global violation handler.
   *
   * <p>This method can be called by the application at startup to change the behavior of the
   * runtime checks (e.g., to switch from throwing exceptions to logging).
   *
   * @param newHandler The new handler to use
   */
  public static void setViolationHandler(ViolationHandler newHandler) {
    if (newHandler == null) {
      throw new IllegalArgumentException("ViolationHandler cannot be null");
    }
    handler = newHandler;
  }

  /**
   * Reports a violation to the current handler.
   *
   * <p>This method is designed to be called by the static check methods in concrete subclasses (the
   * "Static Trampolines").
   *
   * @param checkerName The name of the checker reporting the issue
   * @param message The violation details
   */
  protected static void reportViolation(String checkerName, String message) {
    handler.handleViolation(checkerName, message);
  }
}
