package io.github.eisop.runtimeframework.runtime;

/**
 * The abstract base class for all runtime verifiers.
 *
 * <p>This class serves as the central manager for the {@link ViolationHandler}.
 */
public abstract class RuntimeVerifier {

  // Default to a fail-fast strategy (crashing the application).
  private static volatile ViolationHandler handler;

  static {
    // 1. Try to load from System Property
    String handlerClass = System.getProperty("runtime.handler");
    if (handlerClass != null && !handlerClass.isBlank()) {
      try {
        Class<?> clazz = Class.forName(handlerClass);
        handler = (ViolationHandler) clazz.getConstructor().newInstance();
      } catch (Exception e) {
        System.err.println("[RuntimeFramework] Failed to instantiate handler: " + handlerClass);
        e.printStackTrace();
      }
    }

    // 2. Fallback to Default
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
    handler.handleViolation(checkerName, message);
  }
}
