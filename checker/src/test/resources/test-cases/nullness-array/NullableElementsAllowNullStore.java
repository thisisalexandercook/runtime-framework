import io.github.eisop.runtimeframework.qual.AnnotatedFor;
import org.checkerframework.checker.nullness.qual.Nullable;

@AnnotatedFor("nullness")
public class NullableElementsAllowNullStore {

    public static void main(String[] args) {
        @Nullable String[] values = new @Nullable String[1];
        values[0] = null;
    }
}
