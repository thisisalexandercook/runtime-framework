import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class CheckedFinalVirtualDispatch {
    public static void main(String[] args) {
        CheckedFinalBase receiver = new CheckedFinalSub();
        receiver.produce(new Object());
    }
}

@AnnotatedFor("nullness")
class CheckedFinalBase {
    public Object produce(Object value) {
        return null;
    }
}

@AnnotatedFor("nullness")
class CheckedFinalSub extends CheckedFinalBase {
    public final Object produce(Object value) {
        return new Object();
    }
}
