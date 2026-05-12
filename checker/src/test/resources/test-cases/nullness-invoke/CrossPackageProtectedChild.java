package indyaccess.child;

import indyaccess.base.CrossPackageDispatch.CheckedAccessBase;
import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class CrossPackageProtectedChild extends CheckedAccessBase {
    @Override
    protected void protectedAccept(Object value) {
    }
}
