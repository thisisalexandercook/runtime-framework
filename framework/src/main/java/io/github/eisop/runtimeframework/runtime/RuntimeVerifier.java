package io.github.eisop.runtimeframework.runtime;

import io.github.eisop.runtimeframework.config.RuntimeOptions;

/**
 * The abstract base class for all runtime verifiers.
 *
 * <p>This class serves as the central manager for the {@link ViolationHandler}.
 */
public abstract class RuntimeVerifier {

  // Default to a fail-fast strategy (crashing the application).
  private static volatile ViolationHandler handler;

  static {
    RuntimeOptions options = RuntimeOptions.fromSystemProperties();
    if (options.hasHandlerClassName()) {
      try {
        Class<?> clazz = Class.forName(options.handlerClassName());
        handler = (ViolationHandler) clazz.getConstructor().newInstance();
      } catch (Exception e) {
        System.err.println(
            "[RuntimeFramework] Failed to instantiate handler: " + options.handlerClassName());
        e.printStackTrace();
      }
    }

    if (handler == null) {
      handler = new ThrowingViolationHandler();
    }
  }

  /**
   * Configures the global violation handler.
   *
   * <p>This method can be called by the application at startup to change the behavior of the
   * runtime checks.
   */
  public static void setViolationHandler(ViolationHandler newHandler) {
    if (newHandler == null) {
      throw new IllegalArgumentException("ViolationHandler cannot be null");
    }
    handler = newHandler;
  }

  /** Reports a violation to the current handler. */
  protected static void reportViolation(String checkerName, String message) {
    reportViolation(checkerName, message, AttributionKind.LOCAL);
  }

  /** Reports a violation to the current handler with specific attribution. */
  protected static void reportViolation(
      String checkerName, String message, AttributionKind attribution) {
    handler.handleViolation(checkerName, message, attribution);
  }
}
