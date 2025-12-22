public class UncheckedParent {
    public void dangerousAction(String input) {
        System.out.println("UncheckedParent.dangerousAction: " + input);
    }

    public void overrideMe(String inputA, String inputB) {
	System.out.println("Override this method check" + inputA + inputB);
    }

    protected void protectedAction(String input) {
        System.out.println("UncheckedParent.protectedAction: " + input);
    }

    public final void finalAction(String input) {
        System.out.println("UncheckedParent.finalAction: " + input);
    }

    public String returnAction() {
        System.out.println("UncheckedParent.returnAction returning null...");
        return null;
    }
}
