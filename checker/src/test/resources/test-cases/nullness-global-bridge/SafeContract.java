import io.github.eisop.runtimeframework.qual.AnnotatedFor;
import org.checkerframework.checker.nullness.qual.NonNull;

@AnnotatedFor("nullness")
public class SafeContract {
    public @NonNull String getValue() { return "Safe"; }
}
