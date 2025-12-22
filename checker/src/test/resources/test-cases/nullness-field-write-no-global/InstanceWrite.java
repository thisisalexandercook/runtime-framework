import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class InstanceWrite {
    
    public @NonNull String data = "safe";
    public @Nullable String nullableData = "still safe";

    static class UncheckedInstanceWriter {
        public static void poison(InstanceWrite target) {
            target.data = null;
	    // no error, global off

	    target.nullableData = null;

        }
    }

    public static void main(String[] args) {
        UncheckedInstanceWriter.poison(new InstanceWrite());
    }
}
