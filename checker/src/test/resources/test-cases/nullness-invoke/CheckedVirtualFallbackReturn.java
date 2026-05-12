import io.github.eisop.runtimeframework.qual.AnnotatedFor;
import org.checkerframework.checker.nullness.qual.Nullable;

@AnnotatedFor("nullness")
public class CheckedVirtualFallbackReturn {
    public static void main(String[] args) {
        CheckedVirtualBase checked = new CheckedVirtualBase();
        checked.produce();

        CheckedVirtualBase unchecked = new UncheckedVirtualOverride();
        unchecked.produce();
        // :: error: (Return value of produce (Boundary) must be NonNull)

        CheckedNullableVirtualBase nullable = new UncheckedNullableVirtualOverride();
        nullable.produceNullable();
    }
}

@AnnotatedFor("nullness")
class CheckedVirtualBase {
    public Object produce() {
        return new Object();
    }
}

class UncheckedVirtualOverride extends CheckedVirtualBase {
    public Object produce() {
        return null;
    }
}

@AnnotatedFor("nullness")
class CheckedNullableVirtualBase {
    @Nullable Object produceNullable() {
        return null;
    }
}

class UncheckedNullableVirtualOverride extends CheckedNullableVirtualBase {
    Object produceNullable() {
        return null;
    }
}
