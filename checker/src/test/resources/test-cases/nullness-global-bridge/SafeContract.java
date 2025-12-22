import io.github.eisop.runtimeframework.qual.AnnotatedFor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;


@AnnotatedFor("nullness")
public class SafeContract {
    public @NonNull String getValue() { return "Safe"; }

    public @NonNull String getUnsafeValue() { return "Unsafe"; }

}
