import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class CheckedNativeInterfaceDispatch {
    static class Poison {
        static String VALUE = null;
    }

    public static void main(String[] args) {
        CheckedNativeContract receiver = new CheckedNativeInterfaceImpl();
        boolean reachedNative = false;
        try {
            receiver.produce(new Object());
        } catch (UnsatisfiedLinkError expected) {
            reachedNative = true;
        } catch (AssertionError wrongSafeStub) {
            reachedNative = false;
        }

        if (!reachedNative) {
            System.out.println(Poison.VALUE);
        }
    }
}

@AnnotatedFor("nullness")
interface CheckedNativeContract {
    Object produce(Object value);
}

@AnnotatedFor("nullness")
class CheckedNativeInterfaceImpl implements CheckedNativeContract {
    public native Object produce(Object value);
}
