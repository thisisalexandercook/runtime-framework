import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class Primitives {
    public static void main(String[] args) {
        testPrimitives(42, null, true);
    }

    public static void testPrimitives(int a, String b, boolean c) {
        // :: error: (Parameter 1 must be NonNull)
    }
}
