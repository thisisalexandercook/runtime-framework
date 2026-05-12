public class ArrayOverrideRunner {

    public static void main(String[] args) {
        SafeArrayContract contract = new LegacyArrayTrojan();
        contract.acceptArray(null);
        // :: error: (Parameter 0 in overridden method acceptArray must be NonNull)

        contract.getArray();
    }
}
