import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class CheckedClassTypedInterfaceDefaultDispatch
        implements CheckedClassTypedDefaultContract {
    public static void main(String[] args) {
        CheckedClassTypedInterfaceDefaultDispatch checked =
                new CheckedClassTypedInterfaceDefaultDispatch();
        checked.consume(null);

        UncheckedClassTypedDefaultCaller.call(new CheckedClassTypedInterfaceDefaultDispatch());
    }
}

@AnnotatedFor("nullness")
interface CheckedClassTypedDefaultContract {
    default void consume(Object value) {
    }
}

class UncheckedClassTypedDefaultCaller {
    static void call(CheckedClassTypedInterfaceDefaultDispatch receiver) {
        // :: error: (Parameter 0 must be NonNull)
        receiver.consume(null);
    }
}
