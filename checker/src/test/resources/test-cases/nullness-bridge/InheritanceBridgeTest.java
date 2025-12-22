// :: error: (Parameter 0 in inherited method dangerousAction must be NonNull)
// :: error: (Parameter 0 in inherited method protectedAction must be NonNull)

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class InheritanceBridgeTest extends UncheckedParent {

    public static void main(String[] args) {
        InheritanceBridgeTest test = new InheritanceBridgeTest();
        test.dangerousAction("safe");
        test.dangerousAction(null);

	test.overrideMe("safe", "safe");
	test.overrideMe(null, "unsafe");
	test.overrideMe("safe", "null");

	test.protectedAction("safe");
        test.protectedAction(null);

	test.finalAction("safe");
        test.finalAction(null);
	// cannot bridge final methods, no error here

	String unsafe = test.returnAction();
        // :: error: (Local Variable Assignment (Slot 2) must be NonNull)

	@Nullable String again = test.returnAction();
    }

    @Override
    public void overrideMe(@NonNull String inputA, @Nullable String inputB) {
	// :: error: (Parameter 0 must be NonNull)
	System.out.println("safe version of this method" + inputA + inputB);
    }
}
