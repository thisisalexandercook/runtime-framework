package io.github.eisop.runtimeframework.checker.nullness;

import io.github.eisop.runtimeframework.semantics.CheckerSemantics;
import io.github.eisop.runtimeframework.semantics.ContractResolver;
import io.github.eisop.runtimeframework.semantics.PropertyEmitter;
import io.github.eisop.runtimeframework.semantics.TypeMetadataResolver;

/** Nullness runtime semantics expressed through contracts and property emission. */
public final class NullnessSemantics implements CheckerSemantics {

  private final TypeMetadataResolver typeMetadata = new NullnessTypeMetadataResolver();
  private final ContractResolver contracts = new NullnessContractResolver(typeMetadata);
  private final PropertyEmitter emitter = new NullnessPropertyEmitter();

  @Override
  public ContractResolver contracts() {
    return contracts;
  }

  @Override
  public TypeMetadataResolver typeMetadata() {
    return typeMetadata;
  }

  @Override
  public PropertyEmitter emitter() {
    return emitter;
  }
}
