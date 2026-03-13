import io.github.eisop.runtimeframework.qual.AnnotatedFor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

@AnnotatedFor("nullness")
public class NullableElementsFromField {

    private final @Nullable String[] values = new @Nullable String[1];

    static void accept(@NonNull String value) {}

    public static void main(String[] args) {
        NullableElementsFromField holder = new NullableElementsFromField();
        holder.values[0] = null;
        accept(holder.values[0]);
        // :: error: (Parameter 0 must be NonNull)
    }
}
