package io.github.eisop.runtimeframework.core;

import java.lang.annotation.Annotation;

/**
 * Represents an annotation that explicitly disables strict default checks.
 *
 * <p>For example, in a Nullness system, {@code @Nullable} is an OptOutAnnotation. When present, the
 * policy will skip generating checks.
 */
public record OptOutAnnotation(Class<? extends Annotation> annotationType) {
  // No helper methods needed; the consumer calls annotationType().descriptorString()
}
