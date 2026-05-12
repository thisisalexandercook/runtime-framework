import io.github.eisop.runtimeframework.qual.AnnotatedFor;
import org.checkerframework.checker.nullness.qual.Nullable;

@AnnotatedFor("nullness")
public class ArrayFieldReadReference {

    static class UncheckedLib {
        public String[] poison = null;
    }

    static void consume(@Nullable Object value) {}

    public static void main(String[] args) {
        UncheckedLib lib = new UncheckedLib();
        consume(lib.poison);
        // :: error: (Read Field 'poison' must be NonNull)
    }
}
