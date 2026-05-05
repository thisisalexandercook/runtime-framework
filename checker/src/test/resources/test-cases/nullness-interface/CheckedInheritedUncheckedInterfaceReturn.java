import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class CheckedInheritedUncheckedInterfaceReturn {
    public static void main(String[] args) {
        CheckedReturnChildInterface receiver = new UncheckedReturnInterfaceImpl();
        receiver.produce();
        // :: error: (Return value of produce (Boundary) must be NonNull)
    }
}

interface UncheckedReturnParentInterface {
    Object produce();
}

@AnnotatedFor("nullness")
interface CheckedReturnChildInterface extends UncheckedReturnParentInterface {
}

class UncheckedReturnInterfaceImpl implements CheckedReturnChildInterface {
    public Object produce() {
        return null;
    }
}
