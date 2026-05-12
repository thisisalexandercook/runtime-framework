import io.github.eisop.runtimeframework.qual.AnnotatedFor;
import org.checkerframework.checker.nullness.qual.NonNull;

@AnnotatedFor("nullness")
public class SafeArrayContract {

    public void acceptArray(String @NonNull [] input) {}

    public String @NonNull [] getArray() {
        return new String[1];
    }
}
