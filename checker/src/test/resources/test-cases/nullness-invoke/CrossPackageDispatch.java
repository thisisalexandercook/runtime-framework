package indyaccess.base;

import indyaccess.child.CrossPackagePackageShadowChild;
import indyaccess.child.CrossPackageProtectedChild;
import indyaccess.child.CrossPackageUncheckedPackageShadowChild;
import indyaccess.child.CrossPackageUncheckedProtectedProducerChild;
import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class CrossPackageDispatch {
    public static void main(String[] args) {
        CheckedAccessBase checkedProtected = new CrossPackageProtectedChild();
        checkedProtected.protectedAccept(null);

        CheckedAccessBase checkedPackageShadow = new CrossPackagePackageShadowChild();
        checkedPackageShadow.packageAccept(null);

        CheckedAccessBase uncheckedPackageShadow =
                new CrossPackageUncheckedPackageShadowChild();
        uncheckedPackageShadow.packageAccept(null);
        // :: error: (Parameter 0 must be NonNull)

        CheckedProducerBase uncheckedProtectedProducer =
                new CrossPackageUncheckedProtectedProducerChild();
        uncheckedProtectedProducer.protectedProduce();
        // :: error: (Return value of protectedProduce (Boundary) must be NonNull)
    }

    @AnnotatedFor("nullness")
    public static class CheckedAccessBase {
        protected void protectedAccept(Object value) {
        }

        void packageAccept(Object value) {
        }
    }

    @AnnotatedFor("nullness")
    public static class CheckedProducerBase {
        protected Object protectedProduce() {
            return new Object();
        }
    }
}
