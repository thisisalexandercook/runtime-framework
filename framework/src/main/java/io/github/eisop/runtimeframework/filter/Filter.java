package io.github.eisop.runtimeframework.filter;

@FunctionalInterface
public interface Filter<C> {

  boolean test(C ctx);

  default Filter<C> and(Filter<C> other) {
    return c -> this.test(c) && other.test(c);
  }

  default Filter<C> or(Filter<C> other) {
    return c -> this.test(c) || other.test(c);
  }

  default Filter<C> not() {
    return c -> !this.test(c);
  }

  static <C> Filter<C> acceptAll() {
    return c -> true;
  }

  static <C> Filter<C> rejectAll() {
    return c -> false;
  }
}
