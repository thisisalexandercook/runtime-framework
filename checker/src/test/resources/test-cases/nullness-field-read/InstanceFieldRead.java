import org.checkerframework.checker.nullness.qual.Nullable;
import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class InstanceFieldRead {

    static class UncheckedLib {
        public String poison = null;
    }

    public static void consume(@Nullable Object o) {}

    public static void main(String[] args) {
        UncheckedLib lib = new UncheckedLib();
        
        // 1. Read without storage (Argument passing)
        // :: error: (Read Field 'poison' must be NonNull)
        consume(lib.poison);

        // 2. Assignment
        // :: error: (Read Field 'poison' must be NonNull)
        // :: error: (Local Variable Assignment (Slot 2) must be NonNull)
        String s = lib.poison;

        // 3. Nullable Assignment (Still checks read)
        // :: error: (Read Field 'poison' must be NonNull)
        @Nullable String q = lib.poison;
    }
}