import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class CheckedDefaultBridgeInterfaceDispatch implements CheckedDefaultBridgeChild {
    public static void main(String[] args) {
        CheckedDefaultBridgeParent<String> checked =
                new CheckedDefaultBridgeInterfaceDispatch();
        checked.accept(null);

        UncheckedDefaultBridgeCaller.call(new CheckedDefaultBridgeInterfaceDispatch());
    }
}

@AnnotatedFor("nullness")
interface CheckedDefaultBridgeParent<T> {
    default void accept(T value) {
    }
}

@AnnotatedFor("nullness")
interface CheckedDefaultBridgeChild extends CheckedDefaultBridgeParent<String> {
    default void accept(String value) {
    }
}

class UncheckedDefaultBridgeCaller {
    static void call(CheckedDefaultBridgeParent<String> target) {
        // :: error: (Parameter 0 must be NonNull)
        target.accept(null);
    }
}
