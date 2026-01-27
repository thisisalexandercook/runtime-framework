package io.github.eisop.runtimeframework.policy;

import io.github.eisop.runtimeframework.core.RuntimeVerifier;
import io.github.eisop.runtimeframework.core.TypeSystemConfiguration;
import io.github.eisop.runtimeframework.core.ValidationKind;
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
import java.util.List;

public class StandardEnforcementPolicy implements EnforcementPolicy {

  protected final TypeSystemConfiguration configuration;
  protected final Filter<ClassInfo> safetyFilter;

  public StandardEnforcementPolicy(
      TypeSystemConfiguration configuration, Filter<ClassInfo> safetyFilter) {
    this.configuration = configuration;
    this.safetyFilter = safetyFilter;
  }

  /**
   * Resolves the verifier for a given list of annotations. Logic: 1. Check for any annotation that
   * explicitly ENFORCES. 2. Check for any annotation that explicitly NOOPs. 3. Fallback to default.
   */
  protected RuntimeVerifier resolveVerifier(List<Annotation> annotations) {
    // 1. Look for explicit configuration
    for (Annotation a : annotations) {
      String desc = a.classSymbol().descriptorString();
      TypeSystemConfiguration.ConfigEntry entry = configuration.find(desc);
      if (entry != null) {
        if (entry.kind() == ValidationKind.ENFORCE) {
          return entry.verifier();
        } else if (entry.kind() == ValidationKind.NOOP) {
          return null; // Explicitly skipped
        }
      }
    }

    // 2. Fallback to default
    TypeSystemConfiguration.ConfigEntry defaultEntry = configuration.getDefault();
    if (defaultEntry != null && defaultEntry.kind() == ValidationKind.ENFORCE) {
      return defaultEntry.verifier();
    }

    return null;
  }

  @Override
  public RuntimeVerifier getParameterCheck(MethodModel method, int paramIndex, TypeKind type) {
    if (type != TypeKind.REFERENCE) return null;
    List<Annotation> annos = getMethodParamAnnotations(method, paramIndex);
    return resolveVerifier(annos);
  }

  @Override
  public RuntimeVerifier getFieldWriteCheck(FieldModel field, TypeKind type) {
    return null;
  }

  @Override
  public RuntimeVerifier getFieldReadCheck(FieldModel field, TypeKind type) {
    if (type != TypeKind.REFERENCE) return null;
    List<Annotation> annos = getFieldAnnotations(field);
    return resolveVerifier(annos);
  }

  @Override
  public RuntimeVerifier getReturnCheck(MethodModel method) {
    return null;
  }

  @Override
  public RuntimeVerifier getLocalVariableWriteCheck(MethodModel method, int slot, TypeKind type) {
    if (type != TypeKind.REFERENCE) return null;
    List<Annotation> annos = getLocalVariableAnnotations(method, slot);
    return resolveVerifier(annos);
  }

  @Override
  public RuntimeVerifier getArrayStoreCheck(TypeKind componentType) {
    if (componentType == TypeKind.REFERENCE) {
      TypeSystemConfiguration.ConfigEntry defaultEntry = configuration.getDefault();
      if (defaultEntry != null && defaultEntry.kind() == ValidationKind.ENFORCE) {
        return defaultEntry.verifier();
      }
    }
    return null;
  }

  @Override
  public RuntimeVerifier getArrayLoadCheck(TypeKind componentType) {
    if (componentType == TypeKind.REFERENCE) {
      TypeSystemConfiguration.ConfigEntry defaultEntry = configuration.getDefault();
      if (defaultEntry != null && defaultEntry.kind() == ValidationKind.ENFORCE) {
        return defaultEntry.verifier();
      }
    }
    return null;
  }

  @Override
  public RuntimeVerifier getBoundaryCallCheck(String owner, MethodTypeDesc desc) {
    boolean isUnchecked = !safetyFilter.test(new ClassInfo(owner, null, null));
    TypeKind returnType = TypeKind.from(desc.returnType());

    if (isUnchecked && returnType == TypeKind.REFERENCE) {
      TypeSystemConfiguration.ConfigEntry defaultEntry = configuration.getDefault();
      if (defaultEntry != null && defaultEntry.kind() == ValidationKind.ENFORCE) {
        return defaultEntry.verifier();
      }
    }
    return null;
  }

  @Override
  public RuntimeVerifier getBoundaryFieldReadCheck(String owner, String fieldName, TypeKind type) {
    boolean isUnchecked = !safetyFilter.test(new ClassInfo(owner, null, null));
    if (isUnchecked && type == TypeKind.REFERENCE) {
      TypeSystemConfiguration.ConfigEntry defaultEntry = configuration.getDefault();
      if (defaultEntry != null && defaultEntry.kind() == ValidationKind.ENFORCE) {
        return defaultEntry.verifier();
      }
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
      // Check specific parameter annotations
      boolean explicitNoop = false;
      boolean explicitEnforce = false;

      for (java.lang.annotation.Annotation anno : paramAnnos[i]) {
        String desc = "L" + anno.annotationType().getName().replace('.', '/') + ";";
        TypeSystemConfiguration.ConfigEntry entry = configuration.find(desc);
        if (entry != null) {
          if (entry.kind() == ValidationKind.ENFORCE) explicitEnforce = true;
          if (entry.kind() == ValidationKind.NOOP) explicitNoop = true;
        }
      }

      if (explicitEnforce) return true;

      // If no explicit decision, check default if it's a reference type
      ClassDesc pTypeDesc = ClassDesc.ofDescriptor(paramTypes[i].descriptorString());
      if (TypeKind.from(pTypeDesc) == TypeKind.REFERENCE && !explicitNoop) {
        TypeSystemConfiguration.ConfigEntry defaultEntry = configuration.getDefault();
        if (defaultEntry != null && defaultEntry.kind() == ValidationKind.ENFORCE) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public RuntimeVerifier getBridgeParameterCheck(Method parentMethod, int paramIndex) {
    java.lang.annotation.Annotation[] annos = parentMethod.getParameterAnnotations()[paramIndex];
    Class<?> paramType = parentMethod.getParameterTypes()[paramIndex];

    // 1. Explicit Configuration
    for (java.lang.annotation.Annotation anno : annos) {
      String desc = "L" + anno.annotationType().getName().replace('.', '/') + ";";
      TypeSystemConfiguration.ConfigEntry entry = configuration.find(desc);
      if (entry != null) {
        if (entry.kind() == ValidationKind.ENFORCE) return entry.verifier();
        if (entry.kind() == ValidationKind.NOOP) return null;
      }
    }

    // 2. Default
    ClassDesc pTypeDesc = ClassDesc.ofDescriptor(paramType.descriptorString());
    if (TypeKind.from(pTypeDesc) == TypeKind.REFERENCE) {
      TypeSystemConfiguration.ConfigEntry defaultEntry = configuration.getDefault();
      if (defaultEntry != null && defaultEntry.kind() == ValidationKind.ENFORCE) {
        return defaultEntry.verifier();
      }
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
