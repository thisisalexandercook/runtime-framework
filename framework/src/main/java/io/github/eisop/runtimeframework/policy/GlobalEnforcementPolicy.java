package io.github.eisop.runtimeframework.policy;

import io.github.eisop.runtimeframework.core.TargetAnnotation;
import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.Filter;
import java.lang.classfile.TypeKind;
import java.lang.constant.MethodTypeDesc;
import java.util.Collection;

/**
 * An aggressive policy that enforces checks even inside Unchecked code.
 *
 * <p>This policy is used when the Agent is configured to instrument ALL classes (not just the
 * Checked ones). It detects when Unchecked code interacts with Checked code and injects "Firewall"
 * checks to protect the Trusted boundary.
 */
public class GlobalEnforcementPolicy extends StandardEnforcementPolicy {

  public GlobalEnforcementPolicy(
      Collection<TargetAnnotation> targetAnnotations, Filter<ClassInfo> safetyFilter) {
    super(targetAnnotations, safetyFilter);
  }

  @Override
  public TargetAnnotation getBoundaryFieldWriteCheck(
      String owner, String fieldName, TypeKind type) {
    // GLOBAL LOGIC:
    // We are currently in Unchecked Code (Legacy).
    // We are writing to 'owner'.

    // 1. Is the Target Class (owner) Checked?
    // Note: safetyFilter returns true if the class is "Safe/Checked".
    boolean isTargetChecked = safetyFilter.test(new ClassInfo(owner, null, null));

    // 2. Is it a Reference?
    // 3. Do we have a strict default?
    if (isTargetChecked && type == TypeKind.REFERENCE) {
      // Yes. The Legacy code is attempting to write to a Checked Field.
      // We must enforce NonNull to prevent poisoning the Checked class.
      return super.defaultTarget;
    }

    return null;
  }

  @Override
  public TargetAnnotation getBoundaryMethodOverrideReturnCheck(String owner, MethodTypeDesc desc) {
    // GLOBAL LOGIC:
    // We are in Unchecked Code, and we have overridden a method defined in 'owner'.
    // (The Instrumenter is responsible for determining which class defined the contract).

    // 1. Is the Defining Class (Parent) Checked?
    boolean isParentChecked = safetyFilter.test(new ClassInfo(owner, null, null));

    // 2. Does it return a Reference?
    TypeKind returnType = TypeKind.from(desc.returnType());

    if (isParentChecked && returnType == TypeKind.REFERENCE) {
      // Yes. We are returning a value that the Checked Parent promised would be NonNull.
      // We must check our return value to ensure we honor the Liskov Substitution Principle.
      return super.defaultTarget;
    }
    return null;
  }
}
