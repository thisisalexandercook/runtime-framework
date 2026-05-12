import io.github.eisop.runtimeframework.qual.AnnotatedFor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

@AnnotatedFor("nullness")
public class UncheckedToCheckedArrayStatic {

    public static void checkedArray(String @NonNull [] input) {}

    public static void nullableArray(String @Nullable [] input) {}

    static class UncheckedCaller {
        static void invoke() {
            UncheckedToCheckedArrayStatic.checkedArray(null);
            // :: error: (Parameter 0 must be NonNull)

            UncheckedToCheckedArrayStatic.nullableArray(null);
        }
    }

    public static void main(String[] args) {
        UncheckedCaller.invoke();
    }
}
