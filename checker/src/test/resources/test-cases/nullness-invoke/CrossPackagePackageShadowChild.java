package indyaccess.child;

import indyaccess.base.CrossPackageDispatch.CheckedAccessBase;
import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class CrossPackagePackageShadowChild extends CheckedAccessBase {
    void packageAccept(Object value) {
        throw new AssertionError("package-private shadow should not override base package method");
    }
}
