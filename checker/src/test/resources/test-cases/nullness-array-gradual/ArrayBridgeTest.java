import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class ArrayBridgeTest extends UncheckedParentArrays {

    public static void main(String[] args) {
        ArrayBridgeTest test = new ArrayBridgeTest();

        test.dangerousArray(new String[1]);
        test.dangerousArray(null);
        // :: error: (Parameter 0 in inherited method dangerousArray must be NonNull)

        test.returnArray();
        // :: error: (Return value of inherited method returnArray must be NonNull)
    }
}
