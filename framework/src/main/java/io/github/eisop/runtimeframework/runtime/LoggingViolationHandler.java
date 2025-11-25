package io.github.eisop.runtimeframework.runtime;

import java.io.PrintStream;

/**
 * A violation handler that logs errors to a PrintStream (stderr by default) instead of crashing the
 * application.
 */
public class LoggingViolationHandler implements ViolationHandler {

  private final PrintStream out;

  public LoggingViolationHandler() {
    this(System.err);
  }

  public LoggingViolationHandler(PrintStream out) {
    this.out = out;
  }

  @Override
  public void handleViolation(String checkerName, String message) {
    out.printf("[RuntimeFramework - %s] %s%n", checkerName, message);
  }
}
