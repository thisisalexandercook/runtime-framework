package io.github.eisop.runtimeframework.planning;

import java.util.Objects;

/** Human-readable diagnostic metadata associated with a planned instrumentation action. */
public record DiagnosticSpec(String displayName) {

  public DiagnosticSpec {
    Objects.requireNonNull(displayName, "displayName");
  }

  public static DiagnosticSpec of(String displayName) {
    return new DiagnosticSpec(displayName);
  }
}
