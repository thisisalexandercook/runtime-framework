import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class CheckedBridgeVirtualDispatch {
    public static void main(String[] args) {
        CheckedBridgeBase<String> checked = new CheckedBridgeChild();
        checked.accept(null);

        UncheckedBridgeVirtualCaller.call(new CheckedBridgeChild());
    }
}

@AnnotatedFor("nullness")
class CheckedBridgeBase<T> {
    public void accept(T value) {
    }
}

@AnnotatedFor("nullness")
class CheckedBridgeChild extends CheckedBridgeBase<String> {
    public void accept(String value) {
    }
}

class UncheckedBridgeVirtualCaller {
    static void call(CheckedBridgeBase<String> sink) {
        // :: error: (Parameter 0 must be NonNull)
        sink.accept(null);
    }
}
