import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class CheckedSynchronizedVirtualDispatch {
    public static void main(String[] args) {
        CheckedSynchronizedBase receiver = new CheckedSynchronizedSub();
        receiver.produce(new Object());

        CheckedSynchronizedBase locked = new CheckedSynchronizedLockSub();
        locked.produce(new Object());
    }
}

@AnnotatedFor("nullness")
class CheckedSynchronizedBase {
    public Object produce(Object value) {
        return null;
    }
}

@AnnotatedFor("nullness")
class CheckedSynchronizedSub extends CheckedSynchronizedBase {
    public synchronized Object produce(Object value) {
        return new Object();
    }
}

@AnnotatedFor("nullness")
class CheckedSynchronizedLockSub extends CheckedSynchronizedBase {
    public synchronized Object produce(Object value) {
        if (!Thread.holdsLock(this)) {
            return null;
        }
        return new Object();
    }
}
