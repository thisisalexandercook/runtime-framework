import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class UncheckedInterfaceCaller {
    static class Poison {
        static String VALUE = null;
    }

    public static void main(String[] args) {
        UncheckedCaller.call(new CheckedReceiver());

        // :: error: (Read Field 'VALUE' must be NonNull)
        System.out.println(Poison.VALUE);
    }
}

class UncheckedCaller {
    static void call(CheckedContract receiver) {
        // :: error: (Parameter 0 must be NonNull)
        receiver.consume(null);
    }
}

@AnnotatedFor("nullness")
interface CheckedContract {
    void consume(Object value);
}

@AnnotatedFor("nullness")
class CheckedReceiver implements CheckedContract {
    public void consume(Object value) {
    }
}
