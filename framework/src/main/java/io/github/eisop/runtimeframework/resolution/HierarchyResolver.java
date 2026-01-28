package io.github.eisop.runtimeframework.resolution;

import java.lang.classfile.ClassModel;
import java.util.Set;

/**
 * Responsible for analyzing the inheritance hierarchy of a class to identify methods that are
 * inherited from "Unchecked" parents and require Bridge Methods.
 */
public interface HierarchyResolver {

  /**
   * Identifies methods in the superclass hierarchy that: 1. Are NOT overridden by the current
   * class. 2. Are NOT final/private/static. 3. Come from an "Unchecked" (unsafe) ancestor.
   *
   * @param model The class currently being instrumented.
   * @param loader The ClassLoader to use for loading parent classes (as resources).
   * @return A set of ParentMethod objects representing the targets for bridging.
   */
  Set<ParentMethod> resolveUncheckedMethods(ClassModel model, ClassLoader loader);
}
