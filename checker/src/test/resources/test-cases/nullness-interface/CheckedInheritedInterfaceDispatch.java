import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class CheckedInheritedInterfaceDispatch {
    public static void main(String[] args) {
        CheckedInheritedChildInterface checked = new CheckedInheritedInterfaceImpl();
        checked.accept(null);

        UncheckedInheritedInterfaceCaller.call(new CheckedInheritedInterfaceImpl());
    }
}

@AnnotatedFor("nullness")
interface CheckedInheritedParentInterface {
    void accept(Object value);
}

@AnnotatedFor("nullness")
interface CheckedInheritedChildInterface extends CheckedInheritedParentInterface {
}

@AnnotatedFor("nullness")
class CheckedInheritedInterfaceImpl implements CheckedInheritedChildInterface {
    public void accept(Object value) {
    }
}

class UncheckedInheritedInterfaceCaller {
    static void call(CheckedInheritedChildInterface target) {
        // :: error: (Parameter 0 must be NonNull)
        target.accept(null);
    }
}
