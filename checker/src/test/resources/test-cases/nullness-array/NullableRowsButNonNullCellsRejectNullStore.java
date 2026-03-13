import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class NullableRowsButNonNullCellsRejectNullStore {

    public static void main(String[] args) {
        String[] @org.checkerframework.checker.nullness.qual.Nullable [] grid = new String[1][];
        grid[0] = new String[1];
        grid[0][0] = null;
        // :: error: (Array Element Write must be NonNull)
    }
}
