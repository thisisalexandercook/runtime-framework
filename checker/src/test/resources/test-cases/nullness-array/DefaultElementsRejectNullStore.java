import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class DefaultElementsRejectNullStore {

    public static void main(String[] args) {
        String[] values = new String[1];
        values[0] = null;
        // :: error: (Array Element Write must be NonNull)
    }
}
