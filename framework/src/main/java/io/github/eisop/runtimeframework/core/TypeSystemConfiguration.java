package io.github.eisop.runtimeframework.core;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for a runtime type system.
 *
 * <p>Maps annotations to their validation semantics and verification logic.
 */
public class TypeSystemConfiguration {

  private final Map<String, ConfigEntry> registry = new HashMap<>();
  private ConfigEntry defaultEntry;

  public TypeSystemConfiguration() {
    // A specific checker sets its own default.
    this.defaultEntry = new ConfigEntry(ValidationKind.NOOP, null);
  }

  /**
   * Registers a qualifier that requires enforcement.
   *
   * @param annotation The annotation class.
   * @param verifier The logic to verify the property.
   * @return this configuration (fluent).
   */
  public TypeSystemConfiguration onEnforce(
      Class<? extends Annotation> annotation, CheckGenerator verifier) {
    registry.put(annotation.descriptorString(), new ConfigEntry(ValidationKind.ENFORCE, verifier));
    return this;
  }

  /**
   * Registers a qualifier that requires NO runtime check (a no-op).
   *
   * @param annotation The annotation class.
   * @return this configuration (fluent).
   */
  public TypeSystemConfiguration onNoop(Class<? extends Annotation> annotation) {
    registry.put(annotation.descriptorString(), new ConfigEntry(ValidationKind.NOOP, null));
    return this;
  }

  /**
   * Sets the default behavior when no registered annotation is found on an element.
   *
   * @param kind The validation kind.
   * @param verifier The verifier (required if kind is ENFORCE).
   * @return this configuration (fluent).
   */
  public TypeSystemConfiguration withDefault(ValidationKind kind, CheckGenerator verifier) {
    this.defaultEntry = new ConfigEntry(kind, verifier);
    return this;
  }

  /**
   * Looks up the configuration for a specific annotation descriptor. Returns null if the annotation
   * is not registered (i.e., it is irrelevant).
   */
  public ConfigEntry find(String annotationDescriptor) {
    return registry.get(annotationDescriptor);
  }

  public ConfigEntry getDefault() {
    return defaultEntry;
  }

  public record ConfigEntry(ValidationKind kind, CheckGenerator verifier) {}
}
