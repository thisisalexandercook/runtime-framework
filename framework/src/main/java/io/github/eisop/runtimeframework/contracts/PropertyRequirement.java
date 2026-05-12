package io.github.eisop.runtimeframework.contracts;

import java.util.Objects;

/** A single runtime property obligation such as non-null or committed. */
public record PropertyRequirement(PropertyId propertyId) {

  public PropertyRequirement {
    Objects.requireNonNull(propertyId, "propertyId");
  }

  public static PropertyRequirement of(PropertyId propertyId) {
    return new PropertyRequirement(propertyId);
  }
}
