import io.github.eisop.runtimeframework.qual.AnnotatedFor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;


@AnnotatedFor("nullness")
public class UncheckedToCheckedInstance {

    public void checkedMethod(@NonNull String input) {
    }

    public void nullableCheckedMethod(@Nullable String input) {
    }

    public void mixedCheckedMethod(@Nullable String input, @NonNull String anotherInput) {
    }

    static class UncheckedCaller {
        public static void invoke(UncheckedToCheckedInstance target) {
            // :: error: (Parameter 0 must be NonNull)
            target.checkedMethod(null);
            target.nullableCheckedMethod(null);
            // :: error: (Parameter 1 must be NonNull)
            target.mixedCheckedMethod(null, null);
        }
    }

    public static void main(String[] args) {
        UncheckedToCheckedInstance target = new UncheckedToCheckedInstance();
        UncheckedCaller.invoke(target);
    }
}