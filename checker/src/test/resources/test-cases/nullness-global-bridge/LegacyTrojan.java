public class LegacyTrojan extends SafeContract {
    @Override
    public String getValue() {
        return null;
	// :: error: (Return value of overridden method getValue must be NonNull)
    }
    @Override
    public String getUnsafeValue() {
	return null;
    }

}
