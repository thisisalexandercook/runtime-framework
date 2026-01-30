import org.checkerframework.checker.nullness.qual.Nullable;
import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class NullableBoundary {

    static class UncheckedLib {
        public static String getNull() {
            return null;
        }
    }

    public static void main(String[] args) {
        @Nullable String s = UncheckedLib.getNull();
        // :: error: (Return value of getNull (Boundary) must be NonNull)
    }
}
