import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class NullableRowsAllowNullStore {

    public static void main(String[] args) {
        String[] @org.checkerframework.checker.nullness.qual.Nullable [] grid = new String[1][];
        grid[0] = null;
    }
}
