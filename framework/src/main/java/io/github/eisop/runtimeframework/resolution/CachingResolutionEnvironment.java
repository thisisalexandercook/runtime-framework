package io.github.eisop.runtimeframework.resolution;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeAnnotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Default {@link ResolutionEnvironment} backed by a loader-aware {@link ClassModel} cache. */
final class CachingResolutionEnvironment implements ResolutionEnvironment {

  private final Map<CacheKey, Optional<ClassModel>> classCache = new ConcurrentHashMap<>();

  @Override
  public Optional<ClassModel> loadClass(String internalName, ClassLoader loader) {
    if (internalName == null || internalName.isBlank()) {
      return Optional.empty();
    }

    CacheKey cacheKey = new CacheKey(internalName, loader);
    return classCache.computeIfAbsent(cacheKey, key -> readClassModel(key.internalName(), key.loader()));
  }

  @Override
  public List<LocalVariableTypeAnnotation> getLocalVariableTypeAnnotations(
      MethodModel method, int slot) {
    List<LocalVariableTypeAnnotation> result = new ArrayList<>();
    method
        .code()
        .ifPresent(
            code ->
                code.findAttribute(Attributes.runtimeVisibleTypeAnnotations())
                    .ifPresent(
                        attr -> {
                          for (TypeAnnotation typeAnnotation : attr.annotations()) {
                            if (typeAnnotation.targetInfo()
                                instanceof TypeAnnotation.LocalVarTarget localVarTarget) {
                              for (TypeAnnotation.LocalVarTargetInfo info : localVarTarget.table()) {
                                if (info.index() == slot) {
                                  result.add(
                                      new LocalVariableTypeAnnotation(
                                          typeAnnotation,
                                          info.startLabel(),
                                          info.endLabel(),
                                          slot));
                                }
                              }
                            }
                          }
                        }));
    return List.copyOf(result);
  }

  private Optional<ClassModel> readClassModel(String internalName, ClassLoader loader) {
    String resourcePath = internalName + ".class";
    try (InputStream inputStream =
        loader != null
            ? loader.getResourceAsStream(resourcePath)
            : ClassLoader.getSystemResourceAsStream(resourcePath)) {
      if (inputStream == null) {
        return Optional.empty();
      }
      return Optional.of(ClassFile.of().parse(inputStream.readAllBytes()));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  private record CacheKey(String internalName, ClassLoader loader) {
    @Override
    public int hashCode() {
      return 31 * internalName.hashCode() + System.identityHashCode(loader);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof CacheKey other)) {
        return false;
      }
      return internalName.equals(other.internalName()) && loader == other.loader();
    }
  }
}
