import io.github.eisop.runtimeframework.qual.AnnotatedFor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

@AnnotatedFor("nullness")
public class NullableElementsFromMethodReturn {

    static class UncheckedLib {
        static @Nullable String[] makeValues() {
            return new @Nullable String[1];
        }
    }

    static void accept(@NonNull String value) {}

    public static void main(String[] args) {
        @Nullable String[] values = UncheckedLib.makeValues();
        values[0] = null;
        accept(values[0]);
        // :: error: (Parameter 0 must be NonNull)
    }
}
