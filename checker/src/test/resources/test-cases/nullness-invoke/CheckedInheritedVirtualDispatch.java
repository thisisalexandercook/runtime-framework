import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class CheckedInheritedVirtualDispatch {
    public static void main(String[] args) {
        CheckedInheritedChild checked = new CheckedInheritedChild();
        checked.accept(null);

        UncheckedInheritedVirtualCaller.call(checked);
    }
}

@AnnotatedFor("nullness")
class CheckedInheritedBase {
    public void accept(Object value) {
    }
}

@AnnotatedFor("nullness")
class CheckedInheritedChild extends CheckedInheritedBase {
}

class UncheckedInheritedVirtualCaller {
    static void call(CheckedInheritedChild target) {
        // :: error: (Parameter 0 must be NonNull)
        target.accept(null);
    }
}
