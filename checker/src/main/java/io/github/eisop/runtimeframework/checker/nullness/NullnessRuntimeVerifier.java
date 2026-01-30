package io.github.eisop.runtimeframework.checker.nullness;

import io.github.eisop.runtimeframework.runtime.AttributionKind;
import io.github.eisop.runtimeframework.runtime.RuntimeVerifier;

/**
 * The static trampoline for Nullness checks.
 *
 * <p>The {@link NullnessRuntimeInstrumenter} generates {@code invokestatic} calls to the methods in
 * this class. These methods perform the actual runtime validation and report violations to the
 * central {@link RuntimeVerifier}.
 */
public class NullnessRuntimeVerifier extends RuntimeVerifier {

  /**
   * Verifies that the given object is not null.
   *
   * @param o The object to check
   * @param message The error message to report if the object is null
   */
  public static void checkNotNull(Object o, String message) {
    checkNotNull(o, message, AttributionKind.LOCAL);
  }

  /**
   * Verifies that the given object is not null, with specific attribution.
   *
   * @param o The object to check
   * @param message The error message to report if the object is null
   * @param attribution The attribution strategy
   */
  public static void checkNotNull(Object o, String message, AttributionKind attribution) {
    if (o == null) {
      reportViolation("Nullness", message, attribution);
    }
  }
}
