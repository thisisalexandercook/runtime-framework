package io.github.eisop.runtimeframework.semantics;

import io.github.eisop.runtimeframework.planning.ClassContext;
import io.github.eisop.runtimeframework.planning.MethodContext;
import io.github.eisop.runtimeframework.resolution.ResolutionEnvironment;
import java.lang.classfile.MethodModel;
import java.util.Objects;
import java.util.Optional;

/** Context shared across contract resolution operations. */
public final class ResolutionContext {

  private final ClassContext classContext;
  private final MethodModel enclosingMethod;
  private final ResolutionEnvironment resolutionEnvironment;

  private ResolutionContext(
      ClassContext classContext,
      MethodModel enclosingMethod,
      ResolutionEnvironment resolutionEnvironment) {
    this.classContext = Objects.requireNonNull(classContext, "classContext");
    this.enclosingMethod = enclosingMethod;
    this.resolutionEnvironment =
        Objects.requireNonNull(resolutionEnvironment, "resolutionEnvironment");
  }

  public static ResolutionContext forMethod(
      MethodContext methodContext, ResolutionEnvironment resolutionEnvironment) {
    Objects.requireNonNull(methodContext, "methodContext");
    return new ResolutionContext(
        methodContext.classContext(), methodContext.methodModel(), resolutionEnvironment);
  }

  public static ResolutionContext forClass(
      ClassContext classContext, ResolutionEnvironment resolutionEnvironment) {
    return new ResolutionContext(classContext, null, resolutionEnvironment);
  }

  public ClassContext classContext() {
    return classContext;
  }

  public Optional<MethodModel> enclosingMethod() {
    return Optional.ofNullable(enclosingMethod);
  }

  public ResolutionEnvironment resolutionEnvironment() {
    return resolutionEnvironment;
  }

  public ClassLoader loader() {
    return classContext.classInfo().loader();
  }
}
