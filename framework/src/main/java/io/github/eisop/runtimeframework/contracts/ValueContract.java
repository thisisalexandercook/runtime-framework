package io.github.eisop.runtimeframework.contracts;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** The runtime properties required of a value at a particular sink or boundary. */
public record ValueContract(List<PropertyRequirement> requirements) {

  public ValueContract {
    Objects.requireNonNull(requirements, "requirements");
    requirements = List.copyOf(requirements);
  }

  public static ValueContract none() {
    return new ValueContract(List.of());
  }

  public static ValueContract of(PropertyRequirement... requirements) {
    return new ValueContract(Arrays.asList(requirements));
  }

  public boolean isEmpty() {
    return requirements.isEmpty();
  }

  public boolean requires(PropertyId propertyId) {
    return requirements.stream().anyMatch(requirement -> requirement.propertyId() == propertyId);
  }
}
