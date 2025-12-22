package io.github.eisop.runtimeframework.policy;

import io.github.eisop.runtimeframework.core.OptOutAnnotation;
import io.github.eisop.runtimeframework.core.TargetAnnotation;
import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.Filter;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.reflect.Method;
import java.util.Collection;

public class GlobalEnforcementPolicy extends StandardEnforcementPolicy {

  public GlobalEnforcementPolicy(
      Collection<TargetAnnotation> targetAnnotations,
      Collection<OptOutAnnotation> optOutAnnotations,
      Filter<ClassInfo> safetyFilter) {
    super(targetAnnotations, optOutAnnotations, safetyFilter);
  }

  @Override
  public TargetAnnotation getBoundaryFieldWriteCheck(
      String owner, String fieldName, TypeKind type) {
    if (isClassChecked(owner)) {
      if (type == TypeKind.REFERENCE) {
        if (isFieldOptOut(owner, fieldName)) {
          return null;
        }
        return super.defaultTarget;
      }
    }
    return null;
  }

  @Override
  public TargetAnnotation getUncheckedOverrideReturnCheck(
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
                // Found Checked Parent defining this method.

                // FIX: Check for Opt-Outs on the Parent Method.
                // We must check BOTH Declaration Annotations (e.g. @Deprecated)
                // AND Type Annotations on the return type (e.g. @Nullable String).

                // 1. Declaration Annotations
                for (java.lang.annotation.Annotation anno : m.getAnnotations()) {
                  String desc = "L" + anno.annotationType().getName().replace('.', '/') + ";";
                  if (optOutDescriptors.contains(desc)) {
                    return null;
                  }
                }

                // 2. Type Annotations (Correct place for @Nullable on return)
                for (java.lang.annotation.Annotation anno :
                    m.getAnnotatedReturnType().getAnnotations()) {
                  String desc = "L" + anno.annotationType().getName().replace('.', '/') + ";";
                  if (optOutDescriptors.contains(desc)) {
                    return null;
                  }
                }

                // Check strict default
                TypeKind returnType =
                    TypeKind.from(ClassDesc.ofDescriptor(m.getReturnType().descriptorString()));
                if (returnType == TypeKind.REFERENCE) {
                  return super.defaultTarget;
                }
              }
            }
          }
        }
        parent = parent.getSuperclass();
      }
    } catch (Throwable e) {
      System.out.println("fail");
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
      // Ignore
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
        if (optOutDescriptors.contains(
            "L" + anno.annotationType().getName().replace('.', '/') + ";")) return true;
      }
      for (java.lang.annotation.Annotation anno : field.getAnnotatedType().getAnnotations()) {
        if (optOutDescriptors.contains(
            "L" + anno.annotationType().getName().replace('.', '/') + ";")) return true;
      }
    } catch (Throwable t) {
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
