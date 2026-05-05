import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class CheckedClassTypedInterfaceDefaultReturnBoundary
        implements CheckedChildDefaultReturnContract {
    public static void main(String[] args) {
        CheckedClassTypedInterfaceDefaultReturnBoundary receiver =
                new CheckedClassTypedInterfaceDefaultReturnBoundary();
        receiver.produce();
        // :: error: (Return value of produce (Boundary) must be NonNull)
    }
}

interface UncheckedParentDefaultReturnContract {
    default Object produce() {
        return null;
    }
}

@AnnotatedFor("nullness")
interface CheckedChildDefaultReturnContract extends UncheckedParentDefaultReturnContract {
}
