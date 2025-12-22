public class TrojanRunner {
    public static void main(String[] args) {
        System.out.println("--- Starting Trojan Runner ---");
        SafeContract c = new LegacyTrojan();
        c.getValue();
    }
}
