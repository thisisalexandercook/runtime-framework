package standard;

import io.github.eisop.runtimeframework.qual.AnnotatedFor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

@AnnotatedFor("nullness")
public class DataHolder {
    @NonNull
    public String safeField = "Safe";

    @Nullable
    public String nullableField = null;

    public void setSafe(@NonNull String s) {
        this.safeField = s;
    }
}
