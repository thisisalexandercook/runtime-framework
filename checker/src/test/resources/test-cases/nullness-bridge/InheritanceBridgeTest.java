import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class InheritanceBridgeTest extends UncheckedParent {

    public static void main(String[] args) {
        InheritanceBridgeTest test = new InheritanceBridgeTest();
        test.dangerousAction("safe");

        // :: error: (Parameter 0 in inherited method dangerousAction must be NonNull)
        test.dangerousAction(null);

        test.overrideMe("safe", "safe");

        // :: error: (Parameter 0 must be NonNull)
        test.overrideMe(null, "unsafe");

        test.overrideMe("safe", "null");

        test.protectedAction("safe");

        // :: error: (Parameter 0 in inherited method protectedAction must be NonNull)
        test.protectedAction(null);

        test.finalAction("safe");
        test.finalAction(null);
        // cannot bridge final methods, no error here

        // :: error: (Return value of inherited method returnAction must be NonNull)
        test.returnAction();

        // :: error: (Return value of inherited method returnAction must be NonNull)
        // :: error: (Local Variable Assignment (Slot 2) must be NonNull)
        String unsafe = test.returnAction();

        // :: error: (Return value of inherited method returnAction must be NonNull)
        @Nullable String again = test.returnAction();
    }

    @Override
    public void overrideMe(@NonNull String inputA, @Nullable String inputB) {
        System.out.println("safe version of this method" + inputA + inputB);
    }
}
