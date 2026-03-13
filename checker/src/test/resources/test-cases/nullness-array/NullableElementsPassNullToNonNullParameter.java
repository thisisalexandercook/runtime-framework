import io.github.eisop.runtimeframework.qual.AnnotatedFor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

@AnnotatedFor("nullness")
public class NullableElementsPassNullToNonNullParameter {

    static void accept(@NonNull String value) {}

    public static void main(String[] args) {
        @Nullable String[] values = new @Nullable String[1];
        values[0] = null;
        accept(values[0]);
        // :: error: (Parameter 0 must be NonNull)
    }
}
