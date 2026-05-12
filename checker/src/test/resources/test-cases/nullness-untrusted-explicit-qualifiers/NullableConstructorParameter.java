import io.github.eisop.runtimeframework.qual.AnnotatedFor;
import org.checkerframework.checker.nullness.qual.Nullable;

@AnnotatedFor("nullness")
public class NullableConstructorParameter {
    public static void main(String[] args) {
        // :: error: (Parameter 0 must be NonNull)
        new NullableConstructorParameter(null);
    }

    NullableConstructorParameter(@Nullable String value) {}
}
