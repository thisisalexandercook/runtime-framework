import org.checkerframework.checker.nullness.qual.NonNull;
import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class StaticBoundary {

    static class UncheckedLib {
        public static String getNull() {
            return null;
        }
    }

    public static void main(String[] args) {
        String s = UncheckedLib.getNull();
	// :: error: (Local Variable Assignment (Slot 1) must be NonNull)
    }

}
