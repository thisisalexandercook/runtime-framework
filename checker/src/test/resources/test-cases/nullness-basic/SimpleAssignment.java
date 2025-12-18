import org.checkerframework.checker.nullness.qual.NonNull;

public class SimpleAssignment {
    public static void main(String[] args) {
            test(null);
    }

    public static void test(@NonNull String s) {
        // :: error: (Parameter 0 must be NonNull)
    }
}
