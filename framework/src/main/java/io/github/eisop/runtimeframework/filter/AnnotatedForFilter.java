package io.github.eisop.runtimeframework.filter;

import io.github.eisop.runtimeframework.qual.AnnotatedFor;
import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
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
  private final Map<String, Boolean> cache = new ConcurrentHashMap<>();
  private static final String ANNOTATED_FOR_DESC = AnnotatedFor.class.descriptorString();

  public AnnotatedForFilter(String targetSystem) {
    this.targetSystem = targetSystem;
  }

  /**
   * Checks if the class represented by the given ClassModel is annotated for the a target type system.
   * This also checks the package-level annotation if the class itself is not annotated.
   *
   * @param model The ClassModel of the class to check.
   * @param loader The ClassLoader to use for loading package-info.
   * @return true if the class or its package is annotated for the target system.
   */
  public boolean test(ClassModel model, ClassLoader loader) {
    String className = model.thisClass().asInternalName();

    if (cache.containsKey(className)) {
      return cache.get(className);
    }

    boolean result = hasAnnotatedFor(model);
    if (!result) {
      result = hasPackageLevelAnnotation(className, loader);
    }

    cache.put(className, result);
    return result;
  }

  @Override
  public boolean test(ClassInfo info) {
    String className = info.internalName();
    if (className == null) return false;

    if (cache.containsKey(className)) {
      return cache.get(className);
    }

    boolean result = false;
    String resourcePath = className + ".class";

    try (InputStream is =
        (info.loader() != null)
            ? info.loader().getResourceAsStream(resourcePath)
            : ClassLoader.getSystemResourceAsStream(resourcePath)) {

      if (is != null) {
        ClassModel model = ClassFile.of().parse(is.readAllBytes());
        result = test(model, info.loader());
      }
    } catch (IOException e) {
      System.err.println("[AnnotatedForFilter] Failed to load bytecode for: " + className);
    }

    cache.put(className, result);
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
}
