import io.github.eisop.runtimeframework.qual.AnnotatedFor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

@AnnotatedFor("nullness")
public class UncheckedToCheckedStatic {

    public static void staticCheckedMethod(@NonNull String input) {
        // :: error: (Parameter 0 must be NonNull)
    }

    public static void staticCheckedNullableMethod(@Nullable String input) {
    }

    public static void staticCheckedMixedMethod(@Nullable String input, @NonNull String anotherInput) {
        // :: error: (Parameter 1 must be NonNull)
    }

    static class UncheckedCaller {
        public static void invoke() {
            UncheckedToCheckedStatic.staticCheckedMethod(null);
            UncheckedToCheckedStatic.staticCheckedNullableMethod(null);
            UncheckedToCheckedStatic.staticCheckedMixedMethod(null, null);
        }
    }

    public static void main(String[] args) {
        UncheckedCaller.invoke();
    }
}
