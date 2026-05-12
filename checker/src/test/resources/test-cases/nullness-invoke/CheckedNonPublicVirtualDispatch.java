import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class CheckedNonPublicVirtualDispatch {
    public static void main(String[] args) {
        CheckedNonPublicVirtualTarget target = new CheckedNonPublicVirtualTarget();
        target.protectedAccept(null);
        target.packageAccept(null);

        UncheckedNonPublicVirtualCaller.call(target);
    }
}

@AnnotatedFor("nullness")
class CheckedNonPublicVirtualTarget {
    protected void protectedAccept(Object value) {
    }

    void packageAccept(Object value) {
    }
}

class UncheckedNonPublicVirtualCaller {
    static void call(CheckedNonPublicVirtualTarget target) {
        // :: error: (Parameter 0 must be NonNull)
        target.protectedAccept(null);

        // :: error: (Parameter 0 must be NonNull)
        target.packageAccept(null);
    }
}
