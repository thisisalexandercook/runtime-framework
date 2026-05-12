package io.github.eisop.runtimeframework.filter;

import io.github.eisop.runtimeframework.qual.AnnotatedFor;
import io.github.eisop.runtimeframework.resolution.ResolutionEnvironment;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A filter that checks if a class or its package is annotated with {@link AnnotatedFor} for a
 * specific type system (e.g., "nullness").
 *
 * <p>This filter maintains a cache of results to avoid repeated bytecode parsing. It supports
 * checking both a pre-parsed {@link ClassModel} (for the class currently being transformed) and
 * loading bytecode on-demand (for dependencies).
 */
public class AnnotatedForFilter implements Filter<ClassInfo> {

  private final String targetSystem;
  private final ResolutionEnvironment resolutionEnvironment;
  private final Map<CacheKey, Boolean> cache = new ConcurrentHashMap<>();
  private static final String ANNOTATED_FOR_DESC = AnnotatedFor.class.descriptorString();

  public AnnotatedForFilter(String targetSystem) {
    this(targetSystem, ResolutionEnvironment.system());
  }

  public AnnotatedForFilter(String targetSystem, ResolutionEnvironment resolutionEnvironment) {
    this.targetSystem = targetSystem;
    this.resolutionEnvironment = resolutionEnvironment;
  }

  /**
   * Checks if the class represented by the given ClassModel is annotated for the a target type
   * system. This also checks the package-level annotation if the class itself is not annotated.
   *
   * @param model The ClassModel of the class to check.
   * @param loader The ClassLoader to use for loading package-info.
   * @return true if the class or its package is annotated for the target system.
   */
  public boolean test(ClassModel model, ClassLoader loader) {
    String className = model.thisClass().asInternalName();
    CacheKey cacheKey = new CacheKey(className, loader);

    if (cache.containsKey(cacheKey)) {
      return cache.get(cacheKey);
    }

    boolean result = hasAnnotatedFor(model);
    if (!result) {
      result = hasPackageLevelAnnotation(className, loader);
    }

    cache.put(cacheKey, result);
    return result;
  }

  @Override
  public boolean test(ClassInfo info) {
    String className = info.internalName();
    if (className == null) return false;
    CacheKey cacheKey = new CacheKey(className, info.loader());

    if (cache.containsKey(cacheKey)) {
      return cache.get(cacheKey);
    }

    boolean result =
        resolutionEnvironment
            .loadClass(className, info.loader())
            .map(model -> test(model, info.loader()))
            .orElse(false);

    cache.put(cacheKey, result);
    return result;
  }

  private boolean hasPackageLevelAnnotation(String className, ClassLoader loader) {
    int lastSlash = className.lastIndexOf('/');
    if (lastSlash == -1) return false;

    String packageName = className.substring(0, lastSlash);
    String packageInfoClass = packageName + "/package-info";

    return test(new ClassInfo(packageInfoClass, loader, null));
  }

  private boolean hasAnnotatedFor(ClassModel model) {
    return model
        .findAttribute(Attributes.runtimeVisibleAnnotations())
        .map(
            attr -> {
              for (Annotation anno : attr.annotations()) {
                if (anno.classSymbol().descriptorString().equals(ANNOTATED_FOR_DESC)) {
                  for (var element : anno.elements()) {
                    if (element.name().stringValue().equals("value")) {
                      if (element.value() instanceof AnnotationValue.OfArray arr) {
                        for (AnnotationValue v : arr.values()) {
                          if (v instanceof AnnotationValue.OfString s
                              && s.stringValue().equals(targetSystem)) {
                            return true;
                          }
                        }
                      }
                    }
                  }
                }
              }
              return false;
            })
        .orElse(false);
  }

  private static final class CacheKey {
    private final String className;
    private final ClassLoader loader;

    private CacheKey(String className, ClassLoader loader) {
      this.className = className;
      this.loader = loader;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof CacheKey other)) {
        return false;
      }
      return className.equals(other.className) && loader == other.loader;
    }

    @Override
    public int hashCode() {
      return 31 * className.hashCode() + System.identityHashCode(loader);
    }
  }
}
