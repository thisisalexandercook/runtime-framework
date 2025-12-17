package io.github.eisop.runtimeframework.resolution;

import io.github.eisop.runtimeframework.filter.Filter;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

public class ReflectionHierarchyResolver implements HierarchyResolver {

  private final Filter<String> safetyFilter;

  public ReflectionHierarchyResolver(Filter<String> safetyFilter) {
    this.safetyFilter = safetyFilter;
  }

  @Override
  public Set<Method> resolveUncheckedMethods(ClassModel model, ClassLoader loader) {
    Set<Method> bridgesNeeded = new HashSet<>();
    Set<String> implementedSignatures = new HashSet<>();

    for (MethodModel mm : model.methods()) {
      implementedSignatures.add(
          mm.methodName().stringValue() + mm.methodTypeSymbol().descriptorString());
    }

    String superName =
        model
            .superclass()
            .map(sc -> sc.asInternalName().replace('/', '.'))
            .orElse("java.lang.Object");
    if ("java.lang.Object".equals(superName)) return bridgesNeeded;

    try {
      Class<?> currentAncestor = Class.forName(superName, false, loader);

      while (currentAncestor != null && currentAncestor != Object.class) {
        if (safetyFilter.test(currentAncestor.getName())) {
          break;
        }

        for (Method m : currentAncestor.getDeclaredMethods()) {
          int mods = m.getModifiers();
          if (Modifier.isFinal(mods) || Modifier.isStatic(mods) || Modifier.isPrivate(mods))
            continue;
          if (m.isSynthetic() || m.isBridge()) continue;

          // FIX: Use manual descriptor generation instead of ASM
          String sig = m.getName() + getMethodDescriptor(m);
          if (implementedSignatures.contains(sig)) continue;

          implementedSignatures.add(sig);
          bridgesNeeded.add(m);
        }
        currentAncestor = currentAncestor.getSuperclass();
      }
    } catch (ClassNotFoundException e) {
      // System.err.println("[RuntimeFramework] Could not resolve hierarchy for: " +
      // model.thisClass().asInternalName());
    }
    return bridgesNeeded;
  }

  // Helper to generate descriptor (e.g. "(Ljava/lang/String;)V") using JDK APIs
  private String getMethodDescriptor(Method m) {
    StringBuilder sb = new StringBuilder("(");
    for (Class<?> p : m.getParameterTypes()) {
      sb.append(p.descriptorString());
    }
    sb.append(")");
    sb.append(m.getReturnType().descriptorString());
    return sb.toString();
  }
}
