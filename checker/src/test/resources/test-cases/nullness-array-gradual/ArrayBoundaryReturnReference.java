import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class ArrayBoundaryReturnReference {

    static class UncheckedLib {
        static String[] getNullArray() {
            return null;
        }
    }

    public static void main(String[] args) {
        UncheckedLib.getNullArray();
        // :: error: (Return value of getNullArray (Boundary) must be NonNull)
    }
}
