public class PayloadTest {
    public static void main(String[] args) {
        char GS = '\u001D';
        char US = '\u001F';
        String payload = "DC0401CERT" + GS + "02KOUASSI" + US + "XYSignature";
        
        System.out.println("Payload: " + payload);
        System.out.print("Hex: ");
        for (byte b : payload.getBytes()) {
            System.out.printf("%02X ", b);
        }
        System.out.println();
    }
}
