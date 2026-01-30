import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class FieldArgument {

    static class UncheckedLib {
        public static String POISON = null;
    }

    public static void main(String[] args) {
        // :: error: (Read Field 'POISON' must be NonNull)
        // :: error: (Parameter 0 must be NonNull)
        consume(UncheckedLib.POISON);

        // :: error: (Read Field 'POISON' must be NonNull)
	    nullableConsume(UncheckedLib.POISON);
    }

    public static void consume(@NonNull String arg) {
    }
    
    public static void nullableConsume(@Nullable String arg) {
    }
}