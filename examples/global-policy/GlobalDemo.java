package global; 

public class GlobalDemo {
    public static void main(String[] args) {
        SafeContract c = new LegacyTrojan();


	System.out.println("\n[1] Unchecked override return check");
        try {
	    // cannot break @NonNull contract
            c.getValue();
        } catch (RuntimeException e) {
            System.out.println("SUCCESS: Caught expected violation: " + e.getMessage());
        }

	// ok to overide and return null on a @Nullable contract
	c.getUnsafeValue();

        System.out.println("\n[2] External Write (Instance)");

	SafeContract target = new SafeContract();

        try {
            UncheckedWriter.poisonInstance(target);
            System.err.println("FAILURE: Poisoned instance field!");
        } catch (RuntimeException e) {
            System.out.println("SUCCESS: Caught expected violation: " + e.getMessage());
        }

	System.out.println("\n[3] External Write (Static)");
        try {
            UncheckedWriter.poisonStatic();
            System.err.println("FAILURE: Poisoned static field!");
        } catch (RuntimeException e) {
            System.out.println("SUCCESS: Caught expected violation: " + e.getMessage());
        }
    }
}
