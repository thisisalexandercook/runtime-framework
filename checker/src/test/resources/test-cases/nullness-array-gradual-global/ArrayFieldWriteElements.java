import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class ArrayFieldWriteElements {

    public String[] data = new String[1];

    static class UncheckedWriter {
        static void poison(ArrayFieldWriteElements target) {
            target.data[0] = null;
            // :: error: (Array Element Write must be NonNull)
        }
    }

    public static void main(String[] args) {
        UncheckedWriter.poison(new ArrayFieldWriteElements());
    }
}
