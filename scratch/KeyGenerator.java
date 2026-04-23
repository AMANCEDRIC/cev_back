import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

public class KeyGenerator {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        ECGenParameterSpec ecsp = new ECGenParameterSpec("secp256r1");
        kpg.initialize(ecsp);
        KeyPair kp = kpg.generateKeyPair();

        savePem("src/main/resources/META-INF/privateKey.pem", "PRIVATE KEY", kp.getPrivate().getEncoded());
        savePem("src/main/resources/META-INF/publicKey.pem", "PUBLIC KEY", kp.getPublic().getEncoded());
        
        System.out.println("Clés ECDSA générées avec succès dans src/main/resources/META-INF/");
    }

    private static void savePem(String path, String type, byte[] encoded) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(path);
             OutputStreamWriter osw = new OutputStreamWriter(fos)) {
            osw.write("-----BEGIN " + type + "-----\n");
            String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded);
            osw.write(b64);
            osw.write("\n-----END " + type + "-----\n");
        }
    }
}
