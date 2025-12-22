import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class StaticFieldRead {

    static class UncheckedLib {
        public static String POISON = null;
    }

    public static void main(String[] args) {
        String s = UncheckedLib.POISON;
        // :: error: (Local Variable Assignment (Slot 1) must be NonNull)
    }
}
