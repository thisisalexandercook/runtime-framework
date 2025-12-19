import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;

public class StaticWrite {
    
    public static @Nullable String nullableData = "don't care";
    public static @NonNull String nonNullData = "safe";

    static class UncheckedWriter {
        public static void writeToNullable() {
            StaticWrite.nullableData = null;
        }

        public static void writeToNonNull() {
            StaticWrite.nonNullData = null;
	    // :: error: (Static Field 'nonNullData' must be NonNull)
        }
    }

    public static void main(String[] args) {
        UncheckedWriter.writeToNullable();
        UncheckedWriter.writeToNonNull();
    }
}
