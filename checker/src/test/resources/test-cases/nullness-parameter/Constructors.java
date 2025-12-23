import org.checkerframework.checker.nullness.qual.NonNull;
import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class Constructors {
    public static void main(String[] args) {
        new Constructors(null);
        new Constructors(null, "ignore");
    }

    public Constructors(String s) {
        // :: error: (Parameter 0 must be NonNull)
    }

    public Constructors(@NonNull String a, String b) {
        // :: error: (Parameter 0 must be NonNull)
    }
}
