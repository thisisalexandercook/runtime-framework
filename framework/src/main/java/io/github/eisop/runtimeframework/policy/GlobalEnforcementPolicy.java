package io.github.eisop.runtimeframework.policy;

import io.github.eisop.runtimeframework.core.OptOutAnnotation;
import io.github.eisop.runtimeframework.core.TargetAnnotation;
import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.Filter;
import java.lang.classfile.TypeKind;
import java.lang.constant.MethodTypeDesc;
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
    // GLOBAL LOGIC: Legacy code writing to 'owner'.

    // 1. Is the Target Class (owner) Checked?
    boolean checked = isClassChecked(owner);
    // System.out.println("DEBUG: getBoundaryFieldWriteCheck owner=" + owner + " checked=" +
    // checked);

    if (checked) {
      // 2. Is it a Reference?
      if (type == TypeKind.REFERENCE) {

        // 3. Check for Opt-Outs (e.g. @Nullable) on the target field
        if (isFieldOptOut(owner, fieldName)) {
          return null; // Field allows nulls, so don't check.
        }

        // 4. Default to Strict
        return super.defaultTarget;
      }
    }

    return null;
  }

  private boolean isFieldOptOut(String owner, String fieldName) {
    try {
      // Attempt to load the target class to inspect field annotations.
      // We use the ContextClassLoader as a best-effort resolution strategy.
      Class<?> clazz =
          Class.forName(
              owner.replace('/', '.'), false, Thread.currentThread().getContextClassLoader());
      java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);

      // Check Declaration Annotations
      for (java.lang.annotation.Annotation anno : field.getAnnotations()) {
        String desc = "L" + anno.annotationType().getName().replace('.', '/') + ";";
        if (optOutDescriptors.contains(desc)) {
          return true;
        }
      }

      // Check Type Annotations (e.g. @Nullable String)
      for (java.lang.annotation.Annotation anno : field.getAnnotatedType().getAnnotations()) {
        String desc = "L" + anno.annotationType().getName().replace('.', '/') + ";";
        if (optOutDescriptors.contains(desc)) {
          return true;
        }
      }
    } catch (Throwable t) {
      // If resolution fails (class not found, field not found, security, etc.),
      // we fall back to "False" (Not Opt-Out), which enforces the Strict Check.
      // This is the safe default.
    }
    return false;
  }

  @Override
  public TargetAnnotation getBoundaryMethodOverrideReturnCheck(String owner, MethodTypeDesc desc) {

    TypeKind returnType = TypeKind.from(desc.returnType());

    if (returnType == TypeKind.REFERENCE) {
      return super.defaultTarget;
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
      System.out.println("error finding class");
    }
    return false;
  }
}
