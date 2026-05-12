package io.github.eisop.runtimeframework.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that this declaration should be treated as not annotated for the given type system.
 *
 * <p>This is the negative counterpart to {@link AnnotatedFor}. Scope resolution can use it to let
 * narrower declarations opt out of a checked enclosing class or package.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.PACKAGE})
public @interface UnannotatedFor {
  /**
   * Returns the type systems for which this declaration should be treated as unannotated.
   *
   * @return the type systems for which this declaration should be treated as unannotated
   */
  String[] value();
}
