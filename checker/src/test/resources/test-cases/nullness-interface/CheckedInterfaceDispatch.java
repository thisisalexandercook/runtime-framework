import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class CheckedInterfaceDispatch {

    static class Poison {
        static String VALUE = null;
    }

    public static void main(String[] args) {
        CheckedApi checked = new CheckedImpl();
        checked.accept(null);

        CheckedApi unchecked = new UncheckedImpl();
        unchecked.accept(null);

        // :: error: (Read Field 'VALUE' must be NonNull)
        System.out.println(Poison.VALUE);
    }
}

@AnnotatedFor("nullness")
interface CheckedApi {
    void accept(Object value);
}

@AnnotatedFor("nullness")
class CheckedImpl implements CheckedApi {
    public void accept(Object value) {
    }
}

class UncheckedImpl implements CheckedApi {
    public void accept(Object value) {
    }
}
