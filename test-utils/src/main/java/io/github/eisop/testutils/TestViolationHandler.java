package io.github.eisop.testutils;

import io.github.eisop.runtimeframework.runtime.AttributionKind;
import io.github.eisop.runtimeframework.runtime.ViolationHandler;

/**
 * A specialized handler for integration tests. Lives in test-utils so it doesn't pollute the
 * production framework jar.
 */
public class TestViolationHandler implements ViolationHandler {

  @Override
  public void handleViolation(String checkerName, String message, AttributionKind attribution) {
    StackTraceElement caller = findCaller(attribution);
    String location =
        (caller != null) ? caller.getFileName() + ":" + caller.getLineNumber() : "Unknown:0";

    String output = String.format("[VIOLATION] %s (%s) %s", location, checkerName, message);
    System.out.println(output);
  }

  private StackTraceElement findCaller(AttributionKind attribution) {
    return StackWalker.getInstance()
        .walk(
            stream ->
                stream
                    .filter(f -> !f.getClassName().startsWith("io.github.eisop.runtimeframework"))
                    .filter(f -> !f.getClassName().startsWith("io.github.eisop.testutils"))
                    .skip(attribution == AttributionKind.CALLER ? 1 : 0)
                    .findFirst()
                    .map(StackWalker.StackFrame::toStackTraceElement)
                    .orElse(null));
  }
}
