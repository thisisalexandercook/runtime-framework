package io.github.eisop.runtimeframework.semantics;

import io.github.eisop.runtimeframework.contracts.PropertyRequirement;
import io.github.eisop.runtimeframework.planning.DiagnosticSpec;
import io.github.eisop.runtimeframework.planning.ValueAccess;
import io.github.eisop.runtimeframework.runtime.AttributionKind;
import java.lang.classfile.CodeBuilder;

/** Emits bytecode to enforce an individual runtime property. */
public interface PropertyEmitter {

  void emitCheck(
      CodeBuilder builder,
      PropertyRequirement property,
      ValueAccess access,
      AttributionKind attribution,
      DiagnosticSpec diagnostic);
}
