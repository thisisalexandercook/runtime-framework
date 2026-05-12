import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class CheckedInheritedStaticDispatch {
    public static void main(String[] args) {
        CheckedInheritedStaticChild.accept(null);

        UncheckedInheritedStaticChild.accept(null);
        // :: error: (Parameter 0 must be NonNull)
    }
}

@AnnotatedFor("nullness")
class CheckedInheritedStaticBase {
    public static void accept(Object value) {
    }
}

@AnnotatedFor("nullness")
class CheckedInheritedStaticChild extends CheckedInheritedStaticBase {
}

class UncheckedInheritedStaticChild extends CheckedInheritedStaticBase {
}
