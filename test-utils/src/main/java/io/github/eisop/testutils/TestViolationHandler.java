package io.github.eisop.testutils;

import io.github.eisop.runtimeframework.runtime.ViolationHandler;

/**
 * A specialized handler for integration tests. Lives in test-utils so it doesn't pollute the
 * production framework jar.
 */
public class TestViolationHandler implements ViolationHandler {

  static {
    // DEBUG: Confirm class is loaded by the RuntimeVerifier
    System.err.println("DEBUG: TestViolationHandler class initialized.");
  }

  @Override
  public void handleViolation(String checkerName, String message) {
    // DEBUG: Confirm method is called
    System.err.println("DEBUG: TestViolationHandler.handleViolation invoked. Msg: " + message);

    StackTraceElement caller = findCaller();
    String location =
        (caller != null) ? caller.getFileName() + ":" + caller.getLineNumber() : "Unknown:0";

    String output = String.format("[VIOLATION] %s (%s) %s", location, checkerName, message);
    System.out.println(output);
  }

  private StackTraceElement findCaller() {
    return StackWalker.getInstance()
        .walk(
            stream ->
                stream
                    // Skip the runtime framework internals (Verifier, Handler, etc.)
                    .filter(f -> !f.getClassName().startsWith("io.github.eisop.runtimeframework"))
                    // Skip the test utils (This handler itself)
                    .filter(f -> !f.getClassName().startsWith("io.github.eisop.testutils"))
                    .findFirst()
                    .map(StackWalker.StackFrame::toStackTraceElement)
                    .orElse(null));
  }
}
