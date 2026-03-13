import io.github.eisop.runtimeframework.qual.AnnotatedFor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

@AnnotatedFor("nullness")
public class NullableCellsTwoDimensional {

    static void accept(@NonNull String value) {}

    public static void main(String[] args) {
        @Nullable String[][] grid = new @Nullable String[1][1];
        grid[0][0] = null;
        accept(grid[0][0]);
        // :: error: (Parameter 0 must be NonNull)
    }
}
