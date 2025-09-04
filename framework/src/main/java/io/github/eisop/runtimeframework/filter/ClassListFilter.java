package io.github.eisop.runtimeframework.filter;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Accepts ONLY the classes whose names are listed (exact match).
 * Names may be given as:
 *   - "io.github.eisop.Foo"
 *   - "io/github/eisop/Foo"
 *   - "io/github/eisop/Foo.class"
 * All are normalized to internal names like "io/github/eisop/Foo".
 */
public final class ClassListFilter implements Filter<ClassInfo> {
  private final Set<String> allowed; // normalized internal names

  public ClassListFilter(Collection<String> classNames) {
    Objects.requireNonNull(classNames, "classNames");
    this.allowed = classNames.stream()
        .filter(Objects::nonNull)
        .map(ClassListFilter::toInternalName)
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public boolean test(ClassInfo ctx) {
    String n = ctx.internalName();
    return n != null && allowed.contains(n);
  }

  /** Normalize various name spellings to internal form ("pkg/Cls"). */
  private static String toInternalName(String name) {
    String s = name.trim();
    // strip leading slash
    while (s.startsWith("/")) s = s.substring(1);
    // drop trailing ".class"
    if (s.endsWith(".class")) s = s.substring(0, s.length() - 6);
    // dots -> slashes
    s = s.replace('.', '/');
    return s;
  }
}
