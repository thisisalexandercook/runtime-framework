import org.checkerframework.checker.nullness.qual.NonNull;
import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class Constructors {
    public static void main(String[] args) {
        // :: error: (Parameter 0 must be NonNull)
        new Constructors(null);

        // :: error: (Parameter 0 must be NonNull)
        new Constructors(null, "ignore");
    }

    public Constructors(String s) {
    }

    public Constructors(@NonNull String a, String b) {
    }
}