package standard;

public class UncheckedLibrary {
    
    public static String legacyField = null;

    public static String getLegacyData() {
        System.out.println("\t[UncheckedLibrary] Returning null...");
        return null;
    }

    public static void consumeData(String data) {
        System.out.println("\t[UncheckedLibrary] Consumed: " + data);
    }
}
