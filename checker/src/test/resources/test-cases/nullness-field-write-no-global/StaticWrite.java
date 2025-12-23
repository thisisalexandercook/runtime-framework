import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;
import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class StaticWrite{
    
    public static @Nullable String nullableData = "don't care";
    public static @NonNull String nonNullData = "safe";

    static class UncheckedWriter {
        public static void writeToNullable() {
            StaticWrite.nullableData = null;
        }

        public static void writeToNonNull() {
            StaticWrite.nonNullData = null;
	    // no error global off
        }
    }

    public static void main(String[] args) {
        UncheckedWriter.writeToNullable();
        UncheckedWriter.writeToNonNull();
    }
}
