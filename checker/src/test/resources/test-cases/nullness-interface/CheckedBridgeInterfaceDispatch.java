import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class CheckedBridgeInterfaceDispatch {
    public static void main(String[] args) {
        BridgeInterfaceSink<String> checked = new CheckedStringBridgeInterfaceSink();
        checked.accept(null);

        UncheckedBridgeInterfaceCaller.call(new CheckedStringBridgeInterfaceSink());
    }
}

@AnnotatedFor("nullness")
interface BridgeInterfaceSink<T> {
    void accept(T value);
}

@AnnotatedFor("nullness")
class CheckedStringBridgeInterfaceSink implements BridgeInterfaceSink<String> {
    public void accept(String value) {
    }
}

class UncheckedBridgeInterfaceCaller {
    static void call(BridgeInterfaceSink<String> sink) {
        // :: error: (Parameter 0 must be NonNull)
        sink.accept(null);
    }
}
