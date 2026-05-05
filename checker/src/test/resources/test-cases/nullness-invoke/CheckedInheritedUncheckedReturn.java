import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class CheckedInheritedUncheckedReturn {
    public static void main(String[] args) {
        CheckedReturnChild receiver = new CheckedReturnChild();
        receiver.produce();
        // :: error: (Return value of produce (Boundary) must be NonNull)
    }
}

class UncheckedReturnBase {
    public final Object produce() {
        return null;
    }
}

@AnnotatedFor("nullness")
class CheckedReturnChild extends UncheckedReturnBase {
}
