package io.github.eisop.runtimeframework.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.eisop.runtimeframework.filter.ClassInfo;
import io.github.eisop.runtimeframework.filter.ClassListFilter;
import io.github.eisop.runtimeframework.filter.Filter;
import io.github.eisop.runtimeframework.policy.fixtures.AnnotatedFixture;
import io.github.eisop.runtimeframework.policy.fixtures.UnannotatedFixture;
import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import org.junit.jupiter.api.Test;

public class DefaultRuntimePolicyTest {

  @Test
  public void standardModeWithoutScopeIsSkip() {
    RuntimePolicy policy =
        new DefaultRuntimePolicy(Filter.acceptAll(), Filter.rejectAll(), false, false, "nullness");

    ClassInfo info = classInfo("com/app/Foo", DefaultRuntimePolicyTest.class.getClassLoader());
    assertEquals(ClassClassification.SKIP, policy.classify(info));
  }

  @Test
  public void globalModeWithoutScopeIsUnchecked() {
    RuntimePolicy policy =
        new DefaultRuntimePolicy(Filter.acceptAll(), Filter.rejectAll(), true, false, "nullness");

    ClassInfo info = classInfo("com/app/Foo", DefaultRuntimePolicyTest.class.getClassLoader());
    assertEquals(ClassClassification.UNCHECKED, policy.classify(info));
  }

  @Test
  public void explicitCheckedListMatchesAsChecked() {
    RuntimePolicy policy =
        new DefaultRuntimePolicy(
            Filter.acceptAll(), new ClassListFilter(java.util.List.of("com.app.Foo")), false, false, "nullness");

    ClassInfo hit = classInfo("com/app/Foo", DefaultRuntimePolicyTest.class.getClassLoader());
    assertEquals(ClassClassification.CHECKED, policy.classify(hit));
  }

  @Test
  public void explicitCheckedListMissesAsSkipInStandard() {
    RuntimePolicy policy =
        new DefaultRuntimePolicy(
            Filter.acceptAll(), new ClassListFilter(java.util.List.of("com.app.Foo")), false, false, "nullness");

    ClassInfo miss = classInfo("com/app/Bar", DefaultRuntimePolicyTest.class.getClassLoader());
    assertEquals(ClassClassification.SKIP, policy.classify(miss));
  }

  @Test
  public void explicitCheckedListMissesAsUncheckedInGlobal() {
    RuntimePolicy policy =
        new DefaultRuntimePolicy(
            Filter.acceptAll(), new ClassListFilter(java.util.List.of("com.app.Foo")), true, false, "nullness");

    ClassInfo miss = classInfo("com/app/Bar", DefaultRuntimePolicyTest.class.getClassLoader());
    assertEquals(ClassClassification.UNCHECKED, policy.classify(miss));
  }

  @Test
  public void trustAnnotatedForMarksAnnotatedClassAsChecked() throws IOException {
    RuntimePolicy policy =
        new DefaultRuntimePolicy(Filter.acceptAll(), Filter.rejectAll(), false, true, "nullness");

    ClassModel model = parseClassModel(AnnotatedFixture.class);
    ClassInfo info = classInfo(internalName(AnnotatedFixture.class), AnnotatedFixture.class.getClassLoader());
    assertEquals(ClassClassification.CHECKED, policy.classify(info, model));
  }

  @Test
  public void trustAnnotatedForLeavesUnannotatedClassSkippedInStandard() throws IOException {
    RuntimePolicy policy =
        new DefaultRuntimePolicy(Filter.acceptAll(), Filter.rejectAll(), false, true, "nullness");

    ClassModel model = parseClassModel(UnannotatedFixture.class);
    ClassInfo info =
        classInfo(internalName(UnannotatedFixture.class), UnannotatedFixture.class.getClassLoader());
    assertEquals(ClassClassification.SKIP, policy.classify(info, model));
  }

  @Test
  public void trustAnnotatedForLeavesUnannotatedClassUncheckedInGlobal() throws IOException {
    RuntimePolicy policy =
        new DefaultRuntimePolicy(Filter.acceptAll(), Filter.rejectAll(), true, true, "nullness");

    ClassModel model = parseClassModel(UnannotatedFixture.class);
    ClassInfo info =
        classInfo(internalName(UnannotatedFixture.class), UnannotatedFixture.class.getClassLoader());
    assertEquals(ClassClassification.UNCHECKED, policy.classify(info, model));
  }

  @Test
  public void loaderSensitiveCheckedScopeDifferentiatesSameClassName() {
    ClassLoader trustedLoader = new ClassLoader() {};
    ClassLoader untrustedLoader = new ClassLoader() {};

    Filter<ClassInfo> loaderSensitiveCheckedScope =
        info -> "com/app/Same".equals(info.internalName()) && info.loader() == trustedLoader;

    RuntimePolicy policy =
        new DefaultRuntimePolicy(
            Filter.acceptAll(), loaderSensitiveCheckedScope, false, false, "nullness");

    assertEquals(
        ClassClassification.CHECKED, policy.classify(classInfo("com/app/Same", trustedLoader)));
    assertEquals(
        ClassClassification.SKIP, policy.classify(classInfo("com/app/Same", untrustedLoader)));
  }

  @Test
  public void safetyFilterAlwaysWins() {
    RuntimePolicy policy =
        new DefaultRuntimePolicy(Filter.rejectAll(), Filter.acceptAll(), true, true, "nullness");

    ClassInfo info = classInfo("com/app/Foo", DefaultRuntimePolicyTest.class.getClassLoader());
    assertEquals(ClassClassification.SKIP, policy.classify(info));
  }

  private static ClassInfo classInfo(String internalName, ClassLoader loader) {
    return new ClassInfo(internalName, loader, null);
  }

  private static String internalName(Class<?> clazz) {
    return clazz.getName().replace('.', '/');
  }

  private static ClassModel parseClassModel(Class<?> clazz) throws IOException {
    String resourcePath = internalName(clazz) + ".class";
    try (InputStream is = clazz.getClassLoader().getResourceAsStream(resourcePath)) {
      assertNotNull(is, "Could not load class bytes for " + clazz.getName());
      return ClassFile.of().parse(is.readAllBytes());
    }
  }
}
