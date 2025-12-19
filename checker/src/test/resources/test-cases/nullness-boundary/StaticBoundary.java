import org.checkerframework.checker.nullness.qual.NonNull;

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
