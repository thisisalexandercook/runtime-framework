import org.checkerframework.checker.nullness.qual.Nullable;
import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class StaticFieldRead {

    static class UncheckedLib {
        public static String POISON = null;
    }

    public static void consume(@Nullable Object o) {}

    public static void main(String[] args) {
        // 1. Read without storage
        // :: error: (Read Field 'POISON' must be NonNull)
        consume(UncheckedLib.POISON);

        // 2. Assignment
        // :: error: (Read Field 'POISON' must be NonNull)
        // :: error: (Local Variable Assignment (Slot 1) must be NonNull)
        String s = UncheckedLib.POISON;
    }
}