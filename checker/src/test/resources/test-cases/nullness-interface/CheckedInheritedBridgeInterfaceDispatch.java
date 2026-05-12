import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class CheckedInheritedBridgeInterfaceDispatch {
    public static void main(String[] args) {
        InheritedBridgeSink<String> checked = new CheckedInheritedBridgeChild();
        checked.accept(null);

        UncheckedInheritedBridgeCaller.call(new CheckedInheritedBridgeChild());
    }
}

@AnnotatedFor("nullness")
interface InheritedBridgeSink<T> {
    void accept(T value);
}

@AnnotatedFor("nullness")
class CheckedInheritedBridgeBase {
    public void accept(String value) {
    }
}

@AnnotatedFor("nullness")
class CheckedInheritedBridgeChild
        extends CheckedInheritedBridgeBase
        implements InheritedBridgeSink<String> {
}

class UncheckedInheritedBridgeCaller {
    static void call(InheritedBridgeSink<String> sink) {
        // :: error: (Parameter 0 must be NonNull)
        sink.accept(null);
    }
}
