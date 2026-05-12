import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class CheckedNativeVirtualDispatch {
    static class Poison {
        static String VALUE = null;
    }

    public static void main(String[] args) {
        CheckedNativeBase receiver = new CheckedNativeSub();
        boolean reachedNative = false;
        try {
            receiver.produce(new Object());
        } catch (UnsatisfiedLinkError expected) {
            reachedNative = true;
        }

        if (!reachedNative) {
            System.out.println(Poison.VALUE);
        }
    }
}

@AnnotatedFor("nullness")
class CheckedNativeBase {
    public Object produce(Object value) {
        return null;
    }
}

@AnnotatedFor("nullness")
class CheckedNativeSub extends CheckedNativeBase {
    public native Object produce(Object value);
}
