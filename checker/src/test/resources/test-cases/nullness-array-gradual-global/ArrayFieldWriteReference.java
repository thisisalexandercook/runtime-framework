import io.github.eisop.runtimeframework.qual.AnnotatedFor;
import org.checkerframework.checker.nullness.qual.NonNull;

@AnnotatedFor("nullness")
public class ArrayFieldWriteReference {

    public String @NonNull [] data = new String[1];

    static class UncheckedWriter {
        static void poison(ArrayFieldWriteReference target) {
            target.data = null;
            // :: error: (Field 'data' must be NonNull)
        }
    }

    public static void main(String[] args) {
        UncheckedWriter.poison(new ArrayFieldWriteReference());
    }
}
