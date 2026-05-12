import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class CheckedAbstractVirtualDispatch {
    public static void main(String[] args) {
        CheckedAbstractSink checked = new CheckedAbstractSinkImpl();
        checked.accept(null);

        UncheckedAbstractCaller.call(new CheckedAbstractSinkImpl());

        CheckedPackageAbstractSink packageChecked = new CheckedPackageAbstractSinkImpl();
        packageChecked.packageAccept(null);

        UncheckedPackageAbstractCaller.call(new CheckedPackageAbstractSinkImpl());

        CheckedProtectedAbstractSink protectedChecked = new CheckedProtectedAbstractSinkImpl();
        protectedChecked.protectedAccept(null);

        UncheckedProtectedAbstractCaller.call(new CheckedProtectedAbstractSinkImpl());

        CheckedConcreteSink inheritedUnchecked = new UncheckedConcreteSinkChild();
        inheritedUnchecked.accept(null);
        // :: error: (Parameter 0 must be NonNull)

        CheckedAbstractProducer unchecked = new UncheckedAbstractProducerImpl();
        unchecked.produce();
        // :: error: (Return value of produce (Boundary) must be NonNull)

        CheckedPackageAbstractProducer uncheckedPackage =
                new UncheckedPackageAbstractProducerImpl();
        uncheckedPackage.packageProduce();
        // :: error: (Return value of packageProduce (Boundary) must be NonNull)

        CheckedProtectedAbstractProducer uncheckedProtected =
                new UncheckedProtectedAbstractProducerImpl();
        uncheckedProtected.protectedProduce();
        // :: error: (Return value of protectedProduce (Boundary) must be NonNull)
    }
}

@AnnotatedFor("nullness")
abstract class CheckedAbstractSink {
    public abstract void accept(Object value);
}

@AnnotatedFor("nullness")
class CheckedAbstractSinkImpl extends CheckedAbstractSink {
    public void accept(Object value) {
    }
}

class UncheckedAbstractCaller {
    static void call(CheckedAbstractSink target) {
        // :: error: (Parameter 0 must be NonNull)
        target.accept(null);
    }
}

@AnnotatedFor("nullness")
abstract class CheckedPackageAbstractSink {
    abstract void packageAccept(Object value);
}

@AnnotatedFor("nullness")
class CheckedPackageAbstractSinkImpl extends CheckedPackageAbstractSink {
    void packageAccept(Object value) {
    }
}

class UncheckedPackageAbstractCaller {
    static void call(CheckedPackageAbstractSink target) {
        // :: error: (Parameter 0 must be NonNull)
        target.packageAccept(null);
    }
}

@AnnotatedFor("nullness")
abstract class CheckedProtectedAbstractSink {
    protected abstract void protectedAccept(Object value);
}

@AnnotatedFor("nullness")
class CheckedProtectedAbstractSinkImpl extends CheckedProtectedAbstractSink {
    protected void protectedAccept(Object value) {
    }
}

class UncheckedProtectedAbstractCaller {
    static void call(CheckedProtectedAbstractSink target) {
        // :: error: (Parameter 0 must be NonNull)
        target.protectedAccept(null);
    }
}

@AnnotatedFor("nullness")
class CheckedConcreteSink {
    public void accept(Object value) {
    }
}

class UncheckedConcreteSinkChild extends CheckedConcreteSink {
}

@AnnotatedFor("nullness")
abstract class CheckedAbstractProducer {
    public abstract Object produce();
}

class UncheckedAbstractProducerImpl extends CheckedAbstractProducer {
    public Object produce() {
        return null;
    }
}

@AnnotatedFor("nullness")
abstract class CheckedPackageAbstractProducer {
    abstract Object packageProduce();
}

class UncheckedPackageAbstractProducerImpl extends CheckedPackageAbstractProducer {
    Object packageProduce() {
        return null;
    }
}

@AnnotatedFor("nullness")
abstract class CheckedProtectedAbstractProducer {
    protected abstract Object protectedProduce();
}

class UncheckedProtectedAbstractProducerImpl extends CheckedProtectedAbstractProducer {
    protected Object protectedProduce() {
        return null;
    }
}
