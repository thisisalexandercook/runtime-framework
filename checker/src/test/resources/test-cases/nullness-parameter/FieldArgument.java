import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;


public class FieldArgument {

    static class UncheckedLib {
        public static String POISON = null;
    }

    public static void main(String[] args) {
        consume(UncheckedLib.POISON);
	nullableConsume(UncheckedLib.POISON);
    }

    public static void consume(@NonNull String arg) {
	// :: error: (Parameter 0 must be NonNull)
    }
    
    public static void nullableConsume(@Nullable String arg) {
    }
}
