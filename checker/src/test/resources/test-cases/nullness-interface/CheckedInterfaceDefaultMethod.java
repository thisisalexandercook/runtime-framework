import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class CheckedInterfaceDefaultMethod implements CheckedDefaultContract {

    static class Poison {
        static String VALUE = null;
    }

    public static void main(String[] args) {
        CheckedDefaultContract checked = new CheckedInterfaceDefaultMethod();
        checked.defaultConsume(null);

        UncheckedDefaultCaller.call(new CheckedInterfaceDefaultMethod());

        // :: error: (Read Field 'VALUE' must be NonNull)
        System.out.println(Poison.VALUE);
    }
}

class UncheckedDefaultCaller {
    static void call(CheckedDefaultContract receiver) {
        // :: error: (Parameter 0 must be NonNull)
        receiver.defaultConsume(null);
    }
}

@AnnotatedFor("nullness")
interface CheckedDefaultContract {
    default void defaultConsume(Object value) {
    }
}
