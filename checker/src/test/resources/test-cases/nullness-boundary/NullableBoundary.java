import org.checkerframework.checker.nullness.qual.Nullable;

public class NullableBoundary {

    static class UncheckedLib {
        public static String getNull() {
            return null;
        }
    }

    public static void main(String[] args) {
        @Nullable String s = UncheckedLib.getNull();
    }
}
