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
    // GLOBAL LOGIC:
    // We are currently in Unchecked Code (Legacy).
    // We are writing to 'owner'.

    // 1. Is the Target Class (owner) Checked?
    boolean isTargetChecked = safetyFilter.test(new ClassInfo(owner, null, null));

    // 2. Is it a Reference?
    if (isTargetChecked && type == TypeKind.REFERENCE) {

      // 3. Check for Opt-Outs (e.g. @Nullable) on the target field
      // Since we don't have the ClassModel for 'owner', we try to resolve it via Reflection.
      if (isFieldOptOut(owner, fieldName)) {
        return null; // Field allows nulls, so don't check.
      }

      // 4. Default to Strict
      return super.defaultTarget;
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
    boolean isParentChecked = safetyFilter.test(new ClassInfo(owner, null, null));
    TypeKind returnType = TypeKind.from(desc.returnType());

    if (isParentChecked && returnType == TypeKind.REFERENCE) {
      // Note: Ideally we should perform similar reflection here to check if the
      // Parent method return type is @Nullable.
      // For now, we keep the strict default.
      return super.defaultTarget;
    }
    return null;
  }
}
