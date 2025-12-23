package io.github.eisop.runtimeframework.agent;

import io.github.eisop.runtimeframework.core.RuntimeChecker;
import io.github.eisop.runtimeframework.core.RuntimeInstrumenter;
import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.Filter;
import io.github.eisop.runtimeframework.qual.AnnotatedFor;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RuntimeTransformer implements ClassFileTransformer {

  private final Filter<ClassInfo> scanFilter;
  private final Filter<ClassInfo> policyFilter;
  private final RuntimeChecker checker;
  private final boolean trustAnnotatedFor;
  private final boolean isGlobalMode;

  private final Map<String, Boolean> packageCache = new ConcurrentHashMap<>();
  private static final String ANNOTATED_FOR_DESC = AnnotatedFor.class.descriptorString();

  public RuntimeTransformer(
      Filter<ClassInfo> scanFilter,
      Filter<ClassInfo> policyFilter,
      RuntimeChecker checker,
      boolean trustAnnotatedFor,
      boolean isGlobalMode) {
    this.scanFilter = scanFilter;
    this.policyFilter = policyFilter;
    this.checker = checker;
    this.trustAnnotatedFor = trustAnnotatedFor;
    this.isGlobalMode = isGlobalMode;
  }

  @Override
  public byte[] transform(
      Module module,
      ClassLoader loader,
      String className,
      Class<?> classBeingRedefined,
      ProtectionDomain protectionDomain,
      byte[] classfileBuffer) {

    if (className != null
        && (className.startsWith("java/")
            || className.startsWith("sun/")
            || className.startsWith("jdk/")
            || className.startsWith("org/gradle"))) {
      return null;
    }

    ClassInfo info = new ClassInfo(className, loader, module);

    if (!scanFilter.test(info)) {
      return null;
    }

    System.out.println("[RuntimeFramework] Processing: " + className);

    try {
      ClassFile cf = ClassFile.of();
      ClassModel classModel = cf.parse(classfileBuffer);

      boolean isChecked = policyFilter.test(info);

      if (!isChecked && trustAnnotatedFor) {
        String targetSystem = checker.getName();

        if (hasAnnotatedFor(classModel, targetSystem)) {
          System.out.println("[RuntimeFramework] Auto-detected Checked Class: " + className);
          isChecked = true;
        } else if (hasPackageLevelAnnotation(className, loader, targetSystem)) {
          System.out.println("[RuntimeFramework] Auto-detected Checked Package: " + className);
          isChecked = true;
        }
      }

      if (!isChecked && !isGlobalMode) {
        return null;
      }

      boolean finalIsChecked = isChecked;
      Filter<ClassInfo> dynamicFilter =
          ctx -> {
            if (ctx.internalName().equals(className)) {
              return finalIsChecked;
            }
            return policyFilter.test(ctx);
          };

      RuntimeInstrumenter instrumenter = checker.getInstrumenter(dynamicFilter);
      return cf.transformClass(classModel, instrumenter.asClassTransform(classModel, loader));

    } catch (Throwable t) {
      System.err.println("[RuntimeFramework] CRASH transforming: " + className);
      t.printStackTrace();
      return null;
    }
  }

  private boolean hasPackageLevelAnnotation(String className, ClassLoader loader, String system) {
    int lastSlash = className.lastIndexOf('/');
    if (lastSlash == -1) return false;

    String packageName = className.substring(0, lastSlash);

    if (packageCache.containsKey(packageName)) {
      return packageCache.get(packageName);
    }

    String packageInfoPath = packageName + "/package-info.class";
    boolean found = false;

    try (java.io.InputStream is =
        (loader != null)
            ? loader.getResourceAsStream(packageInfoPath)
            : ClassLoader.getSystemResourceAsStream(packageInfoPath)) {

      if (is != null) {
        ClassModel packageModel = ClassFile.of().parse(is.readAllBytes());
        if (hasAnnotatedFor(packageModel, system)) {
          found = true;
        }
      }
    } catch (Exception e) {
      System.out.println("Cannot get package info");
    }

    packageCache.put(packageName, found);
    return found;
  }

  private boolean hasAnnotatedFor(ClassModel model, String system) {
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
                              && s.stringValue().equals(system)) {
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
