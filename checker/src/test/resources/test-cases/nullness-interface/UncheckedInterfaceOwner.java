import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class UncheckedInterfaceOwner {
    static class Poison {
        static String VALUE = null;
    }

    public static void main(String[] args) {
        UncheckedContract receiver = new CheckedOwnerReceiver();

        // :: error: (Parameter 0 must be NonNull)
        receiver.consume(null);

        // :: error: (Read Field 'VALUE' must be NonNull)
        System.out.println(Poison.VALUE);
    }
}

interface UncheckedContract {
    void consume(Object value);
}

@AnnotatedFor("nullness")
class CheckedOwnerReceiver implements UncheckedContract {
    public void consume(Object value) {
    }
}
