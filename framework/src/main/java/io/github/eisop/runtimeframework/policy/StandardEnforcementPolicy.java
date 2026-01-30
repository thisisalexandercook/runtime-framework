package io.github.eisop.runtimeframework.policy;

import io.github.eisop.runtimeframework.core.RuntimeVerifier;
import io.github.eisop.runtimeframework.core.TypeSystemConfiguration;
import io.github.eisop.runtimeframework.core.ValidationKind;
import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.Filter;
import io.github.eisop.runtimeframework.policy.EnforcementPolicy;
import io.github.eisop.runtimeframework.runtime.AttributionKind;
import io.github.eisop.runtimeframework.resolution.ParentMethod;
import java.lang.classfile.Annotation;
import java.lang.classfile.Attributes;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeAnnotation;
import java.lang.classfile.TypeKind;
import java.lang.constant.MethodTypeDesc;
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
    RuntimeVerifier verifier = resolveVerifier(annos);
    return (verifier != null) ? verifier.withAttribution(AttributionKind.CALLER) : null;
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
  public boolean shouldGenerateBridge(ParentMethod parentMethod) {
    if (parentMethod.owner().thisClass().asInternalName().equals("java/lang/Object")) return false;

    MethodModel method = parentMethod.method();
    // MethodTypeDesc param parsing
    var paramTypes = method.methodTypeSymbol().parameterList();

    // 1. Check Parameters
    for (int i = 0; i < paramTypes.size(); i++) {
      boolean explicitNoop = false;
      boolean explicitEnforce = false;

      List<Annotation> annos = getMethodParamAnnotations(method, i);

      for (Annotation anno : annos) {
        String desc = anno.classSymbol().descriptorString();
        TypeSystemConfiguration.ConfigEntry entry = configuration.find(desc);
        if (entry != null) {
          if (entry.kind() == ValidationKind.ENFORCE) explicitEnforce = true;
          if (entry.kind() == ValidationKind.NOOP) explicitNoop = true;
        }
      }

      if (explicitEnforce) return true;

      // If no explicit decision, check default if it's a reference type
      TypeKind pType = TypeKind.from(paramTypes.get(i));
      if (pType == TypeKind.REFERENCE && !explicitNoop) {
        TypeSystemConfiguration.ConfigEntry defaultEntry = configuration.getDefault();
        if (defaultEntry != null && defaultEntry.kind() == ValidationKind.ENFORCE) {
          return true;
        }
      }
    }

    // 2. Check Return Type
    TypeKind returnType = TypeKind.from(method.methodTypeSymbol().returnType());
    if (returnType == TypeKind.REFERENCE) {
      boolean explicitNoop = false;
      boolean explicitEnforce = false;

      List<Annotation> annos = getMethodReturnAnnotations(method);

      for (Annotation anno : annos) {
        String desc = anno.classSymbol().descriptorString();
        TypeSystemConfiguration.ConfigEntry entry = configuration.find(desc);
        if (entry != null) {
          if (entry.kind() == ValidationKind.ENFORCE) explicitEnforce = true;
          if (entry.kind() == ValidationKind.NOOP) explicitNoop = true;
        }
      }

      if (explicitEnforce) return true;

      if (!explicitNoop) {
        TypeSystemConfiguration.ConfigEntry defaultEntry = configuration.getDefault();
        if (defaultEntry != null && defaultEntry.kind() == ValidationKind.ENFORCE) {
          return true;
        }
      }
    }

    return false;
  }

  @Override
  public RuntimeVerifier getBridgeParameterCheck(ParentMethod parentMethod, int paramIndex) {
    MethodModel method = parentMethod.method();
    List<Annotation> annos = getMethodParamAnnotations(method, paramIndex);

    RuntimeVerifier verifier = resolveVerifier(annos);
    if (verifier != null) return verifier.withAttribution(AttributionKind.CALLER);

    // Check default
    var paramTypes = method.methodTypeSymbol().parameterList();
    TypeKind pType = TypeKind.from(paramTypes.get(paramIndex));

    if (pType == TypeKind.REFERENCE) {
      // Need to ensure we don't default if it was explicitly NOOPed (resolvedVerifier handles NOOP
      // by returning null if found)
      // Re-checking NOOP logic because resolveVerifier returns null for BOTH "Noop" and "Not Found"
      boolean isExplicitNoop = false;
      for (Annotation a : annos) {
        TypeSystemConfiguration.ConfigEntry entry =
            configuration.find(a.classSymbol().descriptorString());
        if (entry != null && entry.kind() == ValidationKind.NOOP) isExplicitNoop = true;
      }

      if (!isExplicitNoop) {
        TypeSystemConfiguration.ConfigEntry defaultEntry = configuration.getDefault();
        if (defaultEntry != null && defaultEntry.kind() == ValidationKind.ENFORCE) {
          return defaultEntry.verifier().withAttribution(AttributionKind.CALLER);
        }
      }
    }
    return null;
  }

  @Override
  public RuntimeVerifier getBridgeReturnCheck(ParentMethod parentMethod) {
    MethodModel method = parentMethod.method();
    TypeKind returnType = TypeKind.from(method.methodTypeSymbol().returnType());
    if (returnType != TypeKind.REFERENCE) return null;

    List<Annotation> annos = getMethodReturnAnnotations(method);

    RuntimeVerifier verifier = resolveVerifier(annos);
    if (verifier != null) return verifier.withAttribution(AttributionKind.CALLER);

    if (returnType == TypeKind.REFERENCE) { // Redundant but keeps structure similar
      boolean isExplicitNoop = false;
      for (Annotation a : annos) {
        TypeSystemConfiguration.ConfigEntry entry =
            configuration.find(a.classSymbol().descriptorString());
        if (entry != null && entry.kind() == ValidationKind.NOOP) isExplicitNoop = true;
      }

      if (!isExplicitNoop) {
        TypeSystemConfiguration.ConfigEntry defaultEntry = configuration.getDefault();
        if (defaultEntry != null && defaultEntry.kind() == ValidationKind.ENFORCE) {
          return defaultEntry.verifier().withAttribution(AttributionKind.CALLER);
        }
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

  private List<Annotation> getMethodReturnAnnotations(MethodModel method) {
    List<Annotation> result = new ArrayList<>();
    method
        .findAttribute(Attributes.runtimeVisibleAnnotations())
        .ifPresent(attr -> result.addAll(attr.annotations()));
    method
        .findAttribute(Attributes.runtimeVisibleTypeAnnotations())
        .ifPresent(
            attr -> {
              for (TypeAnnotation ta : attr.annotations()) {
                if (ta.targetInfo().targetType() == TypeAnnotation.TargetType.METHOD_RETURN) {
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
