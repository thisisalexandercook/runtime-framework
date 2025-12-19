public class InstanceBoundary {

    static class UncheckedLib {
        public String getNull() {
            return null;
        }
    }

    public static void main(String[] args) {
        UncheckedLib lib = new UncheckedLib();
        
        String s = lib.getNull();
	// :: error: (Local Variable Assignment (Slot 2) must be NonNull)

    }
}
