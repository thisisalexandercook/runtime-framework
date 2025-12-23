package standard;

import io.github.eisop.runtimeframework.qual.AnnotatedFor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

@AnnotatedFor("nullness")
public class StandardDemo extends UncheckedParent {

    public static void main(String[] args) {
        System.out.println("=== Standard Policy Demo ===");
        StandardDemo demo = new StandardDemo();

        System.out.println("\n[1] Internal Parameter Check (Strict Default)");
        try {
            demo.internalMethod(null);
        } catch (RuntimeException e) {
            System.out.println("SUCCESS: Caught expected violation: " + e.getMessage());
        }

        System.out.println("\n[2] Bridge Method Check");
        try {
            demo.action(null);
        } catch (RuntimeException e) {
            System.out.println("SUCCESS: Caught expected violation: " + e.getMessage());
        }

        System.out.println("\n[3] Boundary Call (Strict Default)");
        try {
            String s = UncheckedLibrary.getLegacyData();
        } catch (RuntimeException e) {
            System.out.println("SUCCESS: Caught expected violation: " + e.getMessage());
        }
	
        System.out.println("\n[4] Opt-out Check (@Nullable)");
        @Nullable String nullable = UncheckedLibrary.getLegacyData();
        System.out.println("SUCCESS: Allowed null assignment to @Nullable variable.");

        System.out.println("\n[5] Field Safety (Internal Read)");
        DataHolder holder = new DataHolder();
        String val = holder.safeField; 
        System.out.println("Read safe field: " + val);

        System.out.println("\n[6] Boundary Field Read");
        try {
            String s = UncheckedLibrary.legacyField;
        } catch (RuntimeException e) {
            System.out.println("SUCCESS: Caught expected violation: " + e.getMessage());
        }
        
        System.out.println("\n=== Demo Finished ===");
    }

    public void internalMethod(String input) {
        System.out.println("\tInside internalMethod: " + input);
    }
}
