package io.github.eisop.runtimeframework.filter;

public class FrameworkSafetyFilter implements Filter<ClassInfo> {

  @Override
  public boolean test(ClassInfo info) {
    String name = info.internalName();

    if (name == null) return false;

    // 1. Skip JDK classes
    if (name.startsWith("java/")
        || name.startsWith("javax/")
        || name.startsWith("sun/")
        || name.startsWith("jdk/")) {
      return false;
    }

    // 2. Skip the runtime framework itself
    if (name.startsWith("io/github/eisop/")) {
      return false;
    }

    return true;
  }
}
