package io.github.eisop.runtimeframework.runtime;

/** A violation handler that throws a RuntimeException when a check fails. */
public class ThrowingViolationHandler implements ViolationHandler {

  @Override
  public void handleViolation(String checkerName, String message, AttributionKind attribution) {
    StackTraceElement source = findSource(attribution);
    String location =
        (source != null) ? source.getFileName() + ":" + source.getLineNumber() : "Unknown:0";

    throw new RuntimeException(
        String.format("[%s Violation] (%s) %s", checkerName, location, message));
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
