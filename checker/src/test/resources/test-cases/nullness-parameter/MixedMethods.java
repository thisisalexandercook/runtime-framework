import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class MixedMethods {

    public static void main(String[] args) {
        // 1. Explicit NonNull
        checkExplicit(null);

        // 2. Implicit NonNull (Strict Default)
        checkImplicit(null);

        // 3. Explicit Nullable (Should NOT error)
        checkNullable(null);

	// 3. Explicit Nullable (Should NOT error)
        checkMultiple(null,null,null,null);
    }

    public static void checkExplicit(@NonNull String s) {
        // :: error: (Parameter 0 must be NonNull)
    }

    public static void checkImplicit(String s) {
        // :: error: (Parameter 0 must be NonNull)
    }

    public static void checkNullable(@Nullable String s) {
        // No error expected here
    }

    public static void checkMultiple(@Nullable String s, String q, String r, @Nullable String v) {
	// :: error: (Parameter 1 must be NonNull)
	// :: error: (Parameter 2 must be NonNull)
    }
}
