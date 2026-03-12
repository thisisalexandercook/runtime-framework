package io.github.eisop.runtimeframework.semantics;

import io.github.eisop.runtimeframework.contracts.ValueContract;
import io.github.eisop.runtimeframework.planning.TargetRef;

/** Resolves the runtime contract that applies to a specific target. */
public interface ContractResolver {

  ValueContract resolve(TargetRef target, ResolutionContext context);
}
