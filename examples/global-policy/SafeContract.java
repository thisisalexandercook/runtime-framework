package global; 

import io.github.eisop.runtimeframework.qual.AnnotatedFor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

@AnnotatedFor("nullness")
public class SafeContract {

    public @NonNull String safeInstanceField = "Safe";
    public static @NonNull String safeStaticField = "Safe";
    
    public @NonNull String getValue() { return "Safe"; }
    public @Nullable String getUnsafeValue() { return "Unsafe"; }
}
