import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class CheckedNonPublicStaticDispatch {
    public static void main(String[] args) {
        CheckedNonPublicStaticTarget.protectedAccept(null);
        CheckedNonPublicStaticTarget.packageAccept(null);

        UncheckedNonPublicStaticCaller.call();
    }
}

@AnnotatedFor("nullness")
class CheckedNonPublicStaticTarget {
    protected static void protectedAccept(Object value) {
    }

    static void packageAccept(Object value) {
    }
}

class UncheckedNonPublicStaticCaller {
    static void call() {
        // :: error: (Parameter 0 must be NonNull)
        CheckedNonPublicStaticTarget.protectedAccept(null);

        // :: error: (Parameter 0 must be NonNull)
        CheckedNonPublicStaticTarget.packageAccept(null);
    }
}
