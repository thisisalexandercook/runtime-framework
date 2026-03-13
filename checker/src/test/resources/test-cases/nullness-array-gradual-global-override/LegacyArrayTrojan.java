public class LegacyArrayTrojan extends SafeArrayContract {

    @Override
    public void acceptArray(String[] input) {}

    @Override
    public String[] getArray() {
        return null;
        // :: error: (Return value of overridden method getArray must be NonNull)
    }
}
