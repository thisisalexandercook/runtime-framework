package io.github.eisop.runtimeframework.checker.nullness;

import io.github.eisop.runtimeframework.contracts.PropertyId;
import io.github.eisop.runtimeframework.contracts.PropertyRequirement;
import io.github.eisop.runtimeframework.contracts.ValueContract;
import io.github.eisop.runtimeframework.planning.TargetRef;
import io.github.eisop.runtimeframework.semantics.ContractResolver;
import io.github.eisop.runtimeframework.semantics.ResolutionContext;
import io.github.eisop.runtimeframework.semantics.TypeMetadataResolver;
import io.github.eisop.runtimeframework.semantics.TypeUseMetadata;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Resolves nullness contracts for runtime flow targets. */
public final class NullnessContractResolver implements ContractResolver {

  private static final String NON_NULL_DESC = NonNull.class.descriptorString();
  private static final String NULLABLE_DESC = Nullable.class.descriptorString();
  private static final ValueContract NON_NULL_CONTRACT =
      ValueContract.of(PropertyRequirement.of(PropertyId.NON_NULL));
  private final TypeMetadataResolver typeMetadata;

  public NullnessContractResolver() {
    this(new NullnessTypeMetadataResolver());
  }

  public NullnessContractResolver(TypeMetadataResolver typeMetadata) {
    this.typeMetadata = Objects.requireNonNull(typeMetadata, "typeMetadata");
  }

  @Override
  public ValueContract resolve(TargetRef target, ResolutionContext context) {
    return resolveMetadata(typeMetadata.resolve(target, context));
  }

  private ValueContract resolveMetadata(TypeUseMetadata metadata) {
    if (!metadata.isReferenceType()) {
      return ValueContract.none();
    }
    for (var qualifier : metadata.rootQualifiers()) {
      if (NULLABLE_DESC.equals(qualifier.descriptor())) {
        return ValueContract.none();
      }
      if (NON_NULL_DESC.equals(qualifier.descriptor())) {
        return NON_NULL_CONTRACT;
      }
    }
    return NON_NULL_CONTRACT;
  }
}
