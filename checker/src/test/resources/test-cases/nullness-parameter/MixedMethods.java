import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class MixedMethods {

    public static void main(String[] args) {
        // 1. Explicit NonNull
        // :: error: (Parameter 0 must be NonNull)
        checkExplicit(null);

        // 2. Implicit NonNull (Strict Default)
        // :: error: (Parameter 0 must be NonNull)
        checkImplicit(null);

        // 3. Explicit Nullable (Should NOT error)
        checkNullable(null);

        // 4. Multiple Parameters
        // :: error: (Parameter 1 must be NonNull)
        // :: error: (Parameter 2 must be NonNull)
        checkMultiple(null,null,null,null);
    }

    public static void checkExplicit(@NonNull String s) {
    }

    public static void checkImplicit(String s) {
    }

    public static void checkNullable(@Nullable String s) {
    }

    public static void checkMultiple(@Nullable String s, String q, String r, @Nullable String v) {
    }
}