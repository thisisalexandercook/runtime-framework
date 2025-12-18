package io.github.eisop.runtimeframework.policy;

import io.github.eisop.runtimeframework.core.TargetAnnotation;
import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.Filter;
import java.lang.classfile.Annotation;
import java.lang.classfile.Attributes;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeAnnotation;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** The standard policy for Annotation-Driven Runtime Verification. */
public class StandardEnforcementPolicy implements EnforcementPolicy {

  protected final Map<String, TargetAnnotation> targets;
  protected final TargetAnnotation defaultTarget;
  protected final Filter<ClassInfo> safetyFilter;

  public StandardEnforcementPolicy(
      Collection<TargetAnnotation> targetAnnotations, Filter<ClassInfo> safetyFilter) {
    this.targets =
        targetAnnotations.stream()
            .collect(Collectors.toMap(t -> t.annotationType().descriptorString(), t -> t));
    this.defaultTarget = targetAnnotations.stream().findFirst().orElse(null);
    this.safetyFilter = safetyFilter;
  }

  private TargetAnnotation findTarget(List<Annotation> annotations) {
    for (Annotation a : annotations) {
      TargetAnnotation t = targets.get(a.classSymbol().descriptorString());
      if (t != null) return t;
    }
    return null;
  }

  @Override
  public TargetAnnotation getParameterCheck(MethodModel method, int paramIndex, TypeKind type) {
    if (type != TypeKind.REFERENCE) return null;
    TargetAnnotation explicit = findTarget(getMethodParamAnnotations(method, paramIndex));
    if (explicit != null) return explicit;
    return defaultTarget;
  }

  @Override
  public TargetAnnotation getFieldWriteCheck(FieldModel field, TypeKind type) {
    return null;
  }

  @Override
  public TargetAnnotation getFieldReadCheck(FieldModel field, TypeKind type) {
    if (type != TypeKind.REFERENCE) return null;
    return findTarget(getFieldAnnotations(field));
  }

    @Override
    public TargetAnnotation getReturnCheck(MethodModel method) {
        // POLICY CHANGE: Trust internal code.
        // We assume the compiler ensured that a method returning @NonNull actually returns a non-null value.
        // We only check boundaries (Calls/Overrides) or Inputs (Params/Reads).
        return null;
    }

  // --- 2. Boundary Logic ---

  @Override
  public TargetAnnotation getBoundaryCallCheck(String owner, MethodTypeDesc desc) {
    boolean isUnchecked = !safetyFilter.test(new ClassInfo(owner, null, null));
    TypeKind returnType = TypeKind.from(desc.returnType());

    if (isUnchecked && returnType == TypeKind.REFERENCE) {
      return defaultTarget;
    }
    return null;
  }

  @Override
  public TargetAnnotation getBoundaryFieldReadCheck(String owner, String fieldName, TypeKind type) {
    boolean isUnchecked = !safetyFilter.test(new ClassInfo(owner, null, null));
    if (isUnchecked && type == TypeKind.REFERENCE) {
      return defaultTarget;
    }
    return null;
  }

  // --- 3. Inheritance Logic ---

  @Override
  public boolean shouldGenerateBridge(Method parentMethod) {
    Class<?>[] paramTypes = parentMethod.getParameterTypes();
    java.lang.annotation.Annotation[][] paramAnnos = parentMethod.getParameterAnnotations();

    for (int i = 0; i < paramTypes.length; i++) {
      for (java.lang.annotation.Annotation anno : paramAnnos[i]) {
        String desc = "L" + anno.annotationType().getName().replace('.', '/') + ";";
        if (targets.containsKey(desc)) return true;
      }
      ClassDesc pTypeDesc = ClassDesc.ofDescriptor(paramTypes[i].descriptorString());
      if (TypeKind.from(pTypeDesc) == TypeKind.REFERENCE && defaultTarget != null) {
        return true;
      }
    }
    return false;
  }

  @Override
  public TargetAnnotation getBridgeParameterCheck(Method parentMethod, int paramIndex) {
    java.lang.annotation.Annotation[] annos = parentMethod.getParameterAnnotations()[paramIndex];
    Class<?> paramType = parentMethod.getParameterTypes()[paramIndex];

    for (java.lang.annotation.Annotation anno : annos) {
      String desc = "L" + anno.annotationType().getName().replace('.', '/') + ";";
      TargetAnnotation t = targets.get(desc);
      if (t != null) return t;
    }

    ClassDesc pTypeDesc = ClassDesc.ofDescriptor(paramType.descriptorString());
    if (TypeKind.from(pTypeDesc) == TypeKind.REFERENCE) {
      return defaultTarget;
    }
    return null;
  }

  private List<Annotation> getMethodParamAnnotations(MethodModel method, int paramIndex) {
    List<Annotation> result = new ArrayList<>();
    method
        .findAttribute(Attributes.runtimeVisibleParameterAnnotations())
        .ifPresent(
            attr -> {
              List<List<Annotation>> all = attr.parameterAnnotations();
              if (paramIndex < all.size()) result.addAll(all.get(paramIndex));
            });
    method
        .findAttribute(Attributes.runtimeVisibleTypeAnnotations())
        .ifPresent(
            attr -> {
              for (TypeAnnotation ta : attr.annotations()) {
                if (ta.targetInfo() instanceof TypeAnnotation.FormalParameterTarget pt
                    && pt.formalParameterIndex() == paramIndex) {
                  result.add(ta.annotation());
                }
              }
            });
    return result;
  }

  private List<Annotation> getFieldAnnotations(FieldModel field) {
    List<Annotation> result = new ArrayList<>();
    field
        .findAttribute(Attributes.runtimeVisibleAnnotations())
        .ifPresent(attr -> result.addAll(attr.annotations()));

    field
        .findAttribute(Attributes.runtimeVisibleTypeAnnotations())
        .ifPresent(
            attr -> {
              for (TypeAnnotation ta : attr.annotations()) {
                if (ta.targetInfo() instanceof TypeAnnotation.EmptyTarget) {
                  result.add(ta.annotation());
                }
              }
            });
    return result;
  }

  private List<Annotation> getMethodReturnAnnotations(MethodModel method) {
    List<Annotation> result = new ArrayList<>();
    method
        .findAttribute(Attributes.runtimeVisibleTypeAnnotations())
        .ifPresent(
            attr -> {
              for (TypeAnnotation ta : attr.annotations()) {
                // FIX: Use enum comparison, not instanceof
                if (ta.targetInfo().targetType() == TypeAnnotation.TargetType.METHOD_RETURN) {
                  result.add(ta.annotation());
                }
              }
            });
    return result;
  }

    @Override
    public TargetAnnotation getArrayLoadCheck(TypeKind componentType) {
        // Enforce strict defaults on array reads (Defense in Depth).
        // Since arrays can be aliased and modified by Unchecked code (Heap Pollution),
        // we check values upon retrieval to ensure they match our NonNull expectation.
        if (componentType == TypeKind.REFERENCE) {
            return defaultTarget;
        }
        return null;
    }

    @Override
    public TargetAnnotation getArrayStoreCheck(TypeKind componentType) {
        // Enforce strict defaults for arrays in Checked code.
        // We assume all Reference arrays in Checked code are NonNull by default.
        // This prevents Checked code from poisoning its own arrays (or shared arrays) with null.
        if (componentType == TypeKind.REFERENCE) {
            return defaultTarget;
        }
        return null;
    }

}
