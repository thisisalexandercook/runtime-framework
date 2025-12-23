package io.github.eisop.runtimeframework.policy;

import io.github.eisop.runtimeframework.core.OptOutAnnotation;
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
import java.util.Set;
import java.util.stream.Collectors;

public class StandardEnforcementPolicy implements EnforcementPolicy {

  protected final Map<String, TargetAnnotation> targets;
  protected final Set<String> optOutDescriptors;
  protected final TargetAnnotation defaultTarget;
  protected final Filter<ClassInfo> safetyFilter;

  public StandardEnforcementPolicy(
      Collection<TargetAnnotation> targetAnnotations,
      Collection<OptOutAnnotation> optOutAnnotations,
      Filter<ClassInfo> safetyFilter) {

    this.targets =
        targetAnnotations.stream()
            .collect(Collectors.toMap(t -> t.annotationType().descriptorString(), t -> t));

    this.optOutDescriptors =
        optOutAnnotations.stream()
            .map(o -> o.annotationType().descriptorString())
            .collect(Collectors.toSet());

    this.defaultTarget = targetAnnotations.stream().findFirst().orElse(null);
    this.safetyFilter = safetyFilter;
  }

  private TargetAnnotation findTarget(List<Annotation> annotations) {
    for (Annotation a : annotations) {
      String desc = a.classSymbol().descriptorString();
      TargetAnnotation t = targets.get(desc);
      if (t != null) return t;
    }
    return null;
  }

  private boolean hasOptOutAnnotation(List<Annotation> annotations) {
    for (Annotation a : annotations) {
      if (optOutDescriptors.contains(a.classSymbol().descriptorString())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public TargetAnnotation getParameterCheck(MethodModel method, int paramIndex, TypeKind type) {
    if (type != TypeKind.REFERENCE) return null;
    List<Annotation> annos = getMethodParamAnnotations(method, paramIndex);

    TargetAnnotation explicit = findTarget(annos);
    if (explicit != null) return explicit;
    if (hasOptOutAnnotation(annos)) return null;

    return defaultTarget;
  }

  @Override
  public TargetAnnotation getFieldWriteCheck(FieldModel field, TypeKind type) {
    return null;
  }

  @Override
  public TargetAnnotation getFieldReadCheck(FieldModel field, TypeKind type) {
    if (type != TypeKind.REFERENCE) return null;
    List<Annotation> annos = getFieldAnnotations(field);
    TargetAnnotation explicit = findTarget(annos);
    if (explicit != null) return explicit;
    if (hasOptOutAnnotation(annos)) return null;
    return defaultTarget;
  }

  @Override
  public TargetAnnotation getReturnCheck(MethodModel method) {
    return null;
  }

  @Override
  public TargetAnnotation getLocalVariableWriteCheck(MethodModel method, int slot, TypeKind type) {
    if (type != TypeKind.REFERENCE) return null;

    List<Annotation> annos = getLocalVariableAnnotations(method, slot);

    TargetAnnotation explicit = findTarget(annos);
    if (explicit != null) return explicit;
    if (hasOptOutAnnotation(annos)) return null;

    return defaultTarget;
  }

  @Override
  public TargetAnnotation getArrayStoreCheck(TypeKind componentType) {
    if (componentType == TypeKind.REFERENCE) {
      return defaultTarget;
    }
    return null;
  }

  @Override
  public TargetAnnotation getArrayLoadCheck(TypeKind componentType) {
    if (componentType == TypeKind.REFERENCE) {
      return defaultTarget;
    }
    return null;
  }

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
    if (parentMethod.getDeclaringClass() == Object.class) return false;
    Class<?>[] paramTypes = parentMethod.getParameterTypes();
    java.lang.annotation.Annotation[][] paramAnnos = parentMethod.getParameterAnnotations();

    for (int i = 0; i < paramTypes.length; i++) {
      for (java.lang.annotation.Annotation anno : paramAnnos[i]) {
        String desc = "L" + anno.annotationType().getName().replace('.', '/') + ";";
        if (targets.containsKey(desc)) return true;
      }

      ClassDesc pTypeDesc = ClassDesc.ofDescriptor(paramTypes[i].descriptorString());
      if (TypeKind.from(pTypeDesc) == TypeKind.REFERENCE && defaultTarget != null) {
        boolean isOptedOut = false;
        for (java.lang.annotation.Annotation anno : paramAnnos[i]) {
          String desc = "L" + anno.annotationType().getName().replace('.', '/') + ";";
          if (optOutDescriptors.contains(desc)) {
            isOptedOut = true;
            break;
          }
        }
        if (!isOptedOut) return true;
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

    for (java.lang.annotation.Annotation anno : annos) {
      String desc = "L" + anno.annotationType().getName().replace('.', '/') + ";";
      if (optOutDescriptors.contains(desc)) return null;
    }

    ClassDesc pTypeDesc = ClassDesc.ofDescriptor(paramType.descriptorString());
    if (TypeKind.from(pTypeDesc) == TypeKind.REFERENCE) {
      return defaultTarget;
    }
    return null;
  }

  // --- Parsing Helpers ---
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
                    && pt.formalParameterIndex() == paramIndex) result.add(ta.annotation());
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
                if (ta.targetInfo().targetType() == TypeAnnotation.TargetType.FIELD) {
                  result.add(ta.annotation());
                }
              }
            });
    return result;
  }

  private List<Annotation> getLocalVariableAnnotations(MethodModel method, int slot) {
    List<Annotation> result = new ArrayList<>();
    method
        .code()
        .ifPresent(
            code -> {
              code.findAttribute(Attributes.runtimeVisibleTypeAnnotations())
                  .ifPresent(
                      attr -> {
                        for (TypeAnnotation ta : attr.annotations()) {
                          if (ta.targetInfo() instanceof TypeAnnotation.LocalVarTarget localVar) {
                            for (TypeAnnotation.LocalVarTargetInfo info : localVar.table()) {
                              if (info.index() == slot) {
                                result.add(ta.annotation());
                              }
                            }
                          }
                        }
                      });
            });
    return result;
  }
}
