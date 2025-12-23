import org.checkerframework.checker.nullness.qual.Nullable;
import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class InstanceFieldRead {

    static class UncheckedLib {
        public String poison = null;
    }

    public static void main(String[] args) {
        UncheckedLib lib = new UncheckedLib();
        
        String s = lib.poison;
        // :: error: (Local Variable Assignment (Slot 2) must be NonNull)

	@Nullable String q = lib.poison;
    }
}
