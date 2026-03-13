import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class DefaultRowsRejectNullStore {

    public static void main(String[] args) {
        String[][] grid = new String[1][];
        grid[0] = null;
        // :: error: (Array Element Write must be NonNull)
    }
}
