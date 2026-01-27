package io.github.eisop.runtimeframework.policy;

import io.github.eisop.runtimeframework.core.RuntimeVerifier;
import io.github.eisop.runtimeframework.core.TypeSystemConfiguration;
import io.github.eisop.runtimeframework.core.ValidationKind;
import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.Filter;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.reflect.Method;

public class GlobalEnforcementPolicy extends StandardEnforcementPolicy {

  public GlobalEnforcementPolicy(
      TypeSystemConfiguration configuration, Filter<ClassInfo> safetyFilter) {
    super(configuration, safetyFilter);
  }

  @Override
  public RuntimeVerifier getBoundaryFieldWriteCheck(String owner, String fieldName, TypeKind type) {
    if (isClassChecked(owner)) {
      if (type == TypeKind.REFERENCE) {
        if (isFieldOptOut(owner, fieldName)) {
          return null;
        }
        TypeSystemConfiguration.ConfigEntry defaultEntry = configuration.getDefault();
        if (defaultEntry != null && defaultEntry.kind() == ValidationKind.ENFORCE) {
          return defaultEntry.verifier();
        }
      }
    }
    return null;
  }

  @Override
  public RuntimeVerifier getUncheckedOverrideReturnCheck(
      ClassModel classModel, MethodModel method, ClassLoader loader) {
    String superName =
        classModel.superclass().map(sc -> sc.asInternalName().replace('/', '.')).orElse(null);
    if (superName == null || superName.equals("java.lang.Object")) return null;

    try {
      Class<?> parent = Class.forName(superName, false, loader);
      while (parent != null && parent != Object.class) {
        String internalName = parent.getName().replace('.', '/');

        if (isClassChecked(internalName)) {
          for (Method m : parent.getDeclaredMethods()) {
            if (m.getName().equals(method.methodName().stringValue())) {
              String methodDesc = method.methodTypeSymbol().descriptorString();
              String parentDesc = getMethodDescriptor(m);
              if (methodDesc.equals(parentDesc)) {
                // Check parent method annotations
                for (java.lang.annotation.Annotation anno : m.getAnnotations()) {
                  String desc = "L" + anno.annotationType().getName().replace('.', '/') + ";";
                  TypeSystemConfiguration.ConfigEntry entry = configuration.find(desc);
                  if (entry != null && entry.kind() == ValidationKind.NOOP) return null;
                }
                // Check return type annotations
                for (java.lang.annotation.Annotation anno :
                    m.getAnnotatedReturnType().getAnnotations()) {
                  String desc = "L" + anno.annotationType().getName().replace('.', '/') + ";";
                  TypeSystemConfiguration.ConfigEntry entry = configuration.find(desc);
                  if (entry != null && entry.kind() == ValidationKind.NOOP) return null;
                }

                TypeKind returnType =
                    TypeKind.from(ClassDesc.ofDescriptor(m.getReturnType().descriptorString()));
                if (returnType == TypeKind.REFERENCE) {
                  TypeSystemConfiguration.ConfigEntry defaultEntry = configuration.getDefault();
                  if (defaultEntry != null && defaultEntry.kind() == ValidationKind.ENFORCE) {
                    return defaultEntry.verifier();
                  }
                }
              }
            }
          }
        }
        parent = parent.getSuperclass();
      }
    } catch (Throwable e) {
      System.out.println("reflection fail in method override");
    }
    return null;
  }

  private boolean isClassChecked(String internalName) {
    if (safetyFilter.test(new ClassInfo(internalName, null, null))) {
      return true;
    }
    try {
      String className = internalName.replace('/', '.');
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      Class<?> clazz = Class.forName(className, false, cl);
      for (java.lang.annotation.Annotation anno : clazz.getAnnotations()) {
        if (anno.annotationType()
            .getName()
            .equals("io.github.eisop.runtimeframework.qual.AnnotatedFor")) {
          return true;
        }
      }
    } catch (Throwable e) {
      System.out.println("Override reflection fail");
    }
    return false;
  }

  private boolean isFieldOptOut(String owner, String fieldName) {
    try {
      Class<?> clazz =
          Class.forName(
              owner.replace('/', '.'), false, Thread.currentThread().getContextClassLoader());
      java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);

      for (java.lang.annotation.Annotation anno : field.getAnnotations()) {
        String desc = "L" + anno.annotationType().getName().replace('.', '/') + ";";
        TypeSystemConfiguration.ConfigEntry entry = configuration.find(desc);
        if (entry != null && entry.kind() == ValidationKind.NOOP) return true;
      }
      for (java.lang.annotation.Annotation anno : field.getAnnotatedType().getAnnotations()) {
        String desc = "L" + anno.annotationType().getName().replace('.', '/') + ";";
        TypeSystemConfiguration.ConfigEntry entry = configuration.find(desc);
        if (entry != null && entry.kind() == ValidationKind.NOOP) return true;
      }
    } catch (Throwable t) {
      System.out.println("reflection fail in is field opt out");
    }
    return false;
  }

  private String getMethodDescriptor(Method m) {
    StringBuilder sb = new StringBuilder("(");
    for (Class<?> p : m.getParameterTypes()) {
      sb.append(ClassDesc.ofDescriptor(p.descriptorString()).descriptorString());
    }
    sb.append(")");
    sb.append(ClassDesc.ofDescriptor(m.getReturnType().descriptorString()).descriptorString());
    return sb.toString();
  }
}
