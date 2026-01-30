import io.github.eisop.runtimeframework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
public class InstanceBoundary {

    static class UncheckedLib {
        public String getNull() {
            return null;
        }
    }

    public static void main(String[] args) {
        UncheckedLib lib = new UncheckedLib();
        
        String s = lib.getNull();
	// :: error: (Return value of getNull (Boundary) must be NonNull)
	// :: error: (Local Variable Assignment (Slot 2) must be NonNull)

	lib.getNull();
	// :: error: (Return value of getNull (Boundary) must be NonNull)

    }
}
