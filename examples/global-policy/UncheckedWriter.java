package global;

public class UncheckedWriter {

    public static void poisonInstance(SafeContract target) {
        System.out.println("\t[Legacy] Writing null to target.safeInstanceField...");
        // Global Policy instruments this PUTFIELD instruction to prevent poisoning.
        target.safeInstanceField = null; 
    }

    public static void poisonStatic() {
        System.out.println("\t[Legacy] Writing null to CheckedTarget.safeStaticField...");
        // Global Policy instruments this PUTSTATIC instruction.
        SafeContract.safeStaticField = null; 
    }
}
