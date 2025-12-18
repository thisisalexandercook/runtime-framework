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
    boolean isTargetChecked = safetyFilter.test(new ClassInfo(owner, null, null));
    if (isTargetChecked && type == TypeKind.REFERENCE) {
      return super.defaultTarget;
    }
    return null;
  }

  @Override
  public TargetAnnotation getBoundaryMethodOverrideReturnCheck(String owner, MethodTypeDesc desc) {
    boolean isParentChecked = safetyFilter.test(new ClassInfo(owner, null, null));
    TypeKind returnType = TypeKind.from(desc.returnType());

    if (isParentChecked && returnType == TypeKind.REFERENCE) {
      return super.defaultTarget;
    }
    return null;
  }
}
