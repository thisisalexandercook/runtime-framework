package io.github.eisop.runtimeframework.filter;

import java.util.function.Predicate;

@FunctionalInterface
public interface Filter<C> extends Predicate<C> {

  static <C> Filter<C> acceptAll() {
    return c -> true;
  }

  static <C> Filter<C> rejectAll() {
    return c -> false;
  }
}
