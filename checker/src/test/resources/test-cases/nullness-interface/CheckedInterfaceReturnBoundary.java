import io.github.eisop.runtimeframework.qual.AnnotatedFor;
import org.checkerframework.checker.nullness.qual.Nullable;

@AnnotatedFor("nullness")
public class CheckedInterfaceReturnBoundary {
    public static void main(String[] args) {
        ReturningContract checked = new CheckedReturningImpl();
        checked.produce();

        ReturningContract unchecked = new UncheckedReturningImpl();
        unchecked.produce();
        // :: error: (Return value of produce (Boundary) must be NonNull)

        NullableReturningContract nullable = new UncheckedNullableReturningImpl();
        nullable.produceNullable();
    }
}

@AnnotatedFor("nullness")
interface ReturningContract {
    Object produce();
}

@AnnotatedFor("nullness")
class CheckedReturningImpl implements ReturningContract {
    public Object produce() {
        return new Object();
    }
}

class UncheckedReturningImpl implements ReturningContract {
    public Object produce() {
        return null;
    }
}

@AnnotatedFor("nullness")
interface NullableReturningContract {
    @Nullable Object produceNullable();
}

class UncheckedNullableReturningImpl implements NullableReturningContract {
    public Object produceNullable() {
        return null;
    }
}
