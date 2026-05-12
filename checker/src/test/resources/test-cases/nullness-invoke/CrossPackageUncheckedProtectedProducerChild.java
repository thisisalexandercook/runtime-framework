package indyaccess.child;

import indyaccess.base.CrossPackageDispatch.CheckedProducerBase;

public class CrossPackageUncheckedProtectedProducerChild extends CheckedProducerBase {
    @Override
    protected Object protectedProduce() {
        return null;
    }
}
