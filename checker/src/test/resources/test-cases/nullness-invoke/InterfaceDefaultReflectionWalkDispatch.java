import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class InterfaceDefaultReflectionWalkDispatch
        implements CheckedReflectionWalkParent, UncheckedReflectionWalkChild {
    public static void main(String[] args) {
        CheckedReflectionWalkParent receiver = new InterfaceDefaultReflectionWalkDispatch();
        receiver.produce();
        // :: error: (Return value of produce (Boundary) must be NonNull)

        CheckedSpecificDefaultReceiver checkedSpecific =
                new CheckedSpecificDefaultReceiver();
        checkedSpecific.consume(null);
    }
}

@AnnotatedFor("nullness")
interface CheckedReflectionWalkParent {
    default Object produce() {
        return new Object();
    }
}

interface UncheckedReflectionWalkChild extends CheckedReflectionWalkParent {
    @Override
    default Object produce() {
        return null;
    }
}

@AnnotatedFor("nullness")
class CheckedSpecificDefaultReceiver
        implements UncheckedReflectionWalkBase, CheckedReflectionWalkChild {
}

interface UncheckedReflectionWalkBase {
    default void consume(Object value) {
        throw new AssertionError("unchecked parent default should not be selected");
    }
}

@AnnotatedFor("nullness")
interface CheckedReflectionWalkChild extends UncheckedReflectionWalkBase {
    @Override
    default void consume(Object value) {
    }
}
