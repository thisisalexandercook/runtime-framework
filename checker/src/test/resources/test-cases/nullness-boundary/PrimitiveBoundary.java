import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class PrimitiveBoundary {

    static class UncheckedLib {
        public int getInt() {
            return 0;
        }
    }

    public static void main(String[] args) {
        UncheckedLib lib = new UncheckedLib();
        int i = lib.getInt();
    }
}
