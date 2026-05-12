package io.github.eisop.runtimeframework.resolution;

import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.Filter;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

public class BytecodeHierarchyResolver implements HierarchyResolver {

  private final Filter<ClassInfo> checkedScopeFilter;
  private final ResolutionEnvironment resolutionEnvironment;

  public BytecodeHierarchyResolver(Filter<ClassInfo> checkedScopeFilter) {
    this(checkedScopeFilter, ResolutionEnvironment.system());
  }

  public BytecodeHierarchyResolver(
      Filter<ClassInfo> checkedScopeFilter, ResolutionEnvironment resolutionEnvironment) {
    this.checkedScopeFilter = checkedScopeFilter;
    this.resolutionEnvironment = resolutionEnvironment;
  }

  @Override
  public Set<ParentMethod> resolveUncheckedMethods(ClassModel model, ClassLoader loader) {
    Set<ParentMethod> bridgesNeeded = new HashSet<>();
    Set<String> implementedSignatures = new HashSet<>();

    for (MethodModel mm : model.methods()) {
      implementedSignatures.add(
          mm.methodName().stringValue() + mm.methodTypeSymbol().descriptorString());
    }

    String superName =
        model
            .superclass()
            .map(sc -> sc.asInternalName().replace('/', '.'))
            .orElse("java.lang.Object");

    if ("java.lang.Object".equals(superName)) return bridgesNeeded;

    String currentName = superName;
    while (currentName != null && !currentName.equals("java.lang.Object")) {
      String currentInternalName = currentName.replace('.', '/');
      if (checkedScopeFilter.test(new ClassInfo(currentInternalName, loader, null))) {
        break;
      }

      var parentModelOpt = resolutionEnvironment.loadClass(currentInternalName, loader);
      if (parentModelOpt.isEmpty()) {
        break;
      }

      ClassModel parentModel = parentModelOpt.get();

      for (MethodModel m : parentModel.methods()) {
        int flags = m.flags().flagsMask();

        if (Modifier.isPrivate(flags)
            || Modifier.isStatic(flags)
            || Modifier.isFinal(flags)
            || (flags & 0x1000) != 0 /* SYNTHETIC */
            || (flags & 0x0040) != 0 /* BRIDGE */) {
          continue;
        }

        String sig = m.methodName().stringValue() + m.methodTypeSymbol().descriptorString();
        if (implementedSignatures.contains(sig)) continue;

        implementedSignatures.add(sig);
        bridgesNeeded.add(new ParentMethod(parentModel, m));
      }

      currentName =
          parentModel.superclass().map(sc -> sc.asInternalName().replace('/', '.')).orElse(null);
    }
    return bridgesNeeded;
  }
}
