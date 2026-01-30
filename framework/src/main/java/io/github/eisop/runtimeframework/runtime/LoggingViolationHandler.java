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
  public void handleViolation(String checkerName, String message, AttributionKind attribution) {
    StackTraceElement source = findSource(attribution);
    String location =
        (source != null) ? source.getFileName() + ":" + source.getLineNumber() : "Unknown:0";

    out.printf("[RuntimeFramework - %s] (%s) %s%n", checkerName, location, message);
  }

  private StackTraceElement findSource(AttributionKind attribution) {
    return StackWalker.getInstance()
        .walk(
            stream ->
                stream
                    // Skip the runtime framework infrastructure
                    .filter(f -> !f.getClassName().startsWith("io.github.eisop.runtimeframework"))
                    // Skip the method that triggered the violation if we are attributing to the
                    // CALLER
                    .skip(attribution == AttributionKind.CALLER ? 1 : 0)
                    .findFirst()
                    .map(StackWalker.StackFrame::toStackTraceElement)
                    .orElse(null));
  }
}
