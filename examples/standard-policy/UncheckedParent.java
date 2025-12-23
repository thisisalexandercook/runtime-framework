package standard;

public class UncheckedParent {
    // Legacy method with no annotations.
    // In a Checked child, this implies 's' must be NonNull.
    public void action(String s) {
        System.out.println("\t[UncheckedParent] action: " + s);
    }

    // A method returning null. 
    // The bridge might not check this (depending on return policy), 
    // but the caller in Checked code will check the result upon assignment.
    public String getValue() {
        return null; 
    }
}
