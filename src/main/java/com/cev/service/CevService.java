package com.cev.service;
 
import com.cev.model.TypeDocument;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CEV Service — Cellule d'Émission et de Vérification
 *
 * Responsabilités :
 *  1. Générer une signature HMAC-SHA256 du document
 *  2. Construire le payload du DataMatrix (référence + hash + métadonnées)
 *  3. Encoder ce payload en DataMatrix (image PNG base64)
 *  4. Vérifier l'authenticité d'un document à partir du payload scanné
 */
@ApplicationScoped
public class CevService {

    private static final Logger LOG = Logger.getLogger(CevService.class);
    private static final String SIGN_ALGO = "SHA256withECDSA";

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @ConfigProperty(name = "cev.organisation.code", defaultValue = "CEV")
    String organisationCode;

    @ConfigProperty(name = "cev.document.base-url", defaultValue = "http://localhost:8080")
    String baseUrl;

    @ConfigProperty(name = "cev.2ddoc.authority-id", defaultValue = "CI03")
    String authorityId;

    @ConfigProperty(name = "cev.2ddoc.cert-id", defaultValue = "0001")
    String certId;

    @ConfigProperty(name = "cev.2ddoc.perimeter", defaultValue = "CI")
    String perimeter;

    @ConfigProperty(name = "cev.2ddoc.version", defaultValue = "04")
    String version;

    private static final char GS = '\u001D'; // Group Separator (entre les champs)
    private static final char US = '\u001F'; // Unit Separator (avant la signature)

    @PostConstruct
    public void init() {
        try {
            this.privateKey = loadPrivateKey("/META-INF/privateKey.pem");
            this.publicKey = loadPublicKey("/META-INF/publicKey.pem");
            LOG.info("Clés ECDSA chargées avec succès pour 2D-Doc.");
        } catch (Exception e) {
            LOG.error("Erreur fatale : Impossible de charger les clés ECDSA depuis META-INF.", e);
        }
    }

    private PrivateKey loadPrivateKey(String path) throws Exception {
        String key = readPemFile(path);
        byte[] encoded = Base64.getMimeDecoder().decode(key);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
        KeyFactory kf = KeyFactory.getInstance("EC");
        return kf.generatePrivate(keySpec);
    }

    private PublicKey loadPublicKey(String path) throws Exception {
        String key = readPemFile(path);
        byte[] encoded = Base64.getMimeDecoder().decode(key);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
        KeyFactory kf = KeyFactory.getInstance("EC");
        return kf.generatePublic(keySpec);
    }

    private String readPemFile(String path) throws Exception {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) throw new RuntimeException("Fichier non trouvé: " + path);
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return content.replaceAll("-----(BEGIN|END) (PRIVATE|PUBLIC) KEY-----", "").replaceAll("\\s", "");
        }
    }

    public String signerTexte(String texte) {
        try {
            Signature sig = Signature.getInstance(SIGN_ALGO);
            sig.initSign(privateKey);
            sig.update(texte.getBytes(StandardCharsets.UTF_8));
            byte[] derSignature = sig.sign();
            
            // Conversion DER -> RAW (64 octets pour P-256)
            byte[] rawSignature = derToRaw(derSignature);
            return bytesToHex(rawSignature);
        } catch (Exception e) {
            LOG.errorf("Erreur signature ECDSA : %s", e.getMessage());
            throw new RuntimeException("Erreur lors de la signature du document", e);
        }
    }

    private byte[] derToRaw(byte[] der) throws Exception {
        // Un objet DER ECDSA est : 0x30 <len> 0x02 <lenR> <R> 0x02 <lenS> <S>
        int offset = 2; // skip 30 and len
        
        // Lire R
        offset++; // skip 02
        int lenR = der[offset++];
        int startR = offset;
        offset += lenR;
        
        // Lire S
        offset++; // skip 02
        int lenS = der[offset++];
        int startS = offset;
        
        byte[] raw = new byte[64];
        
        // Copier R (32 octets de fin)
        int rOffset = Math.max(0, lenR - 32);
        int rCopyLen = Math.min(32, lenR);
        System.arraycopy(der, startR + rOffset, raw, 32 - rCopyLen, rCopyLen);
        
        // Copier S (32 octets de fin)
        int sOffset = Math.max(0, lenS - 32);
        int sCopyLen = Math.min(32, lenS);
        System.arraycopy(der, startS + sOffset, raw, 64 - sCopyLen, sCopyLen);
        
        return raw;
    }

    // -----------------------------------------------
    // 2. CONSTRUCTION DU PAYLOAD DATAMATRIX
    // -----------------------------------------------

    /**
     * Construit le contenu texte complet qui respecte le standard 2D-Doc.
     */
    public String buildFinalPayload(String reference, String nom, String prenom,
                                     TypeDocument type, LocalDate dateEmission,
                                     LocalDate dateExpiration, Map<String, Object> donnees) {
        String dataPart = buildDataPart(reference, nom, prenom, type, dateEmission, dateExpiration, donnees);
        // Dans le standard, le payload est séparé de la signature par un US unique
        String payload = dataPart;
        if (payload.endsWith(String.valueOf(GS))) {
            payload = payload.substring(0, payload.length() - 1);
        }
        
        String totalToSign = payload + US + "XY";
        String signatureHex = signerTexte(totalToSign);
        
        // Pour obtenir 104 caractères au total (XY + 102 chars), on encode en Base32 sans padding
        String signatureB32 = encodeBase32NoPadding(hexToBytes(signatureHex));
        if (signatureB32.length() > 101) {
            signatureB32 = signatureB32.substring(0, 101);
        }
        
        // On ajoute un '=' pour arriver à 102 chars de bloc data + XY = 104
        return payload + US + "XY" + signatureB32 + "=";
    }

    public String buildDataPart(String reference, String nom, String prenom,
                                 TypeDocument type, LocalDate dateEmission,
                                 LocalDate dateExpiration, Map<String, Object> donnees) {
        StringBuilder sb = new StringBuilder();

        // 1. Header 2D-Doc (22 caractères fixes)
        sb.append("DC").append(version); // 4
        sb.append(authorityId.length() > 4 ? authorityId.substring(0, 4) : String.format("%-4s", authorityId)); // 4
        sb.append(certId.length() > 4 ? certId.substring(0, 4) : String.format("%4s", certId).replace(' ', '0')); // 4
        sb.append(encodeDate(dateEmission)); // 4
        sb.append(encodeDate(LocalDate.now())); // 4 (Date de signature)
        sb.append(mapDocType(type)); // 2
        sb.append(perimeter.length() > 2 ? perimeter.substring(0, 2) : String.format("%-2s", perimeter)); // 2

        // 2. Données (Tags)
        appendField(sb, "01", reference);
        appendField(sb, "02", nom.toUpperCase().trim());
        if (prenom != null && !prenom.isBlank()) {
            appendField(sb, "03", prenom.toUpperCase().trim());
        }

        // Tags additionnels depuis les données
        if (donnees != null) {
            if (donnees.containsKey("dateNaissance")) {
                appendField(sb, "04", normalizeDate(String.valueOf(donnees.get("dateNaissance"))));
            }
            if (donnees.containsKey("lieuNaissance")) {
                appendField(sb, "05", String.valueOf(donnees.get("lieuNaissance")));
            }
            if (donnees.containsKey("nationalite")) {
                appendField(sb, "06", String.valueOf(donnees.get("nationalite")));
            }
            if (donnees.containsKey("autoriteNom")) {
                appendField(sb, "09", String.valueOf(donnees.get("autoriteNom")));
            }
        }

        if (dateExpiration != null) {
            appendField(sb, "08", dateExpiration.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        }

        return sb.toString();
    }

    private String encodeDate(LocalDate date) {
        if (date == null) return "0000";
        long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.of(2000, 1, 1), date);
        return String.format("%04X", days);
    }

    private String mapDocType(TypeDocument type) {
        if (type == null) return "00";
        return switch (type) {
            case CERTIFICAT -> "15";
            case DIPLOME -> "16";
            case ATTESTATION -> "17";
            case FACTURE -> "18";
            default -> "00";
        };
    }

    private void appendField(StringBuilder sb, String tag, String value) {
        if (value == null || value.isBlank()) return;
        sb.append(tag).append(value).append(GS);
    }

    // -----------------------------------------------
    // 3. GÉNÉRATION IMAGE DATAMATRIX (ZXing)
    // -----------------------------------------------

    /**
     * Génère une image DataMatrix PNG encodée en Base64.
     *
     * @param payload contenu à encoder
     * @param taille  taille en pixels (ex: 150)
     * @return image PNG en Base64
     */
    public String genererDatamatrixBase64(String payload, int taille) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            MultiFormatWriter writer = new MultiFormatWriter();
            BitMatrix matrix = writer.encode(payload, BarcodeFormat.DATA_MATRIX, taille, taille, hints);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", baos);

            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            LOG.errorf("Erreur génération DataMatrix : %s", e.getMessage());
            throw new RuntimeException("Erreur génération DataMatrix", e);
        }
    }

    /**
     * Génère le DataMatrix avec taille par défaut (150px).
     */
    public String genererDatamatrixBase64(String payload) {
        return genererDatamatrixBase64(payload, 150);
    }

    /**
     * Retourne l'image DataMatrix sous forme de bytes PNG.
     */
    public byte[] genererDatamatrixBytes(String payload, int taille) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            MultiFormatWriter writer = new MultiFormatWriter();
            BitMatrix matrix = writer.encode(payload, BarcodeFormat.DATA_MATRIX, taille, taille, hints);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur génération DataMatrix bytes", e);
        }
    }

    // -----------------------------------------------
    // 4. VÉRIFICATION D'AUTHENTICITÉ
    // -----------------------------------------------

    /**
     * Vérifie qu'un document est authentique en recalculant son hash
     * et en le comparant à celui du payload DataMatrix scanné.
     */
    public boolean verifierDocument(String payloadComplet) {
        try {
            if (payloadComplet == null) return false;

            // La signature commence après le seul US du document
            int usIdx = payloadComplet.indexOf(US);
            if (usIdx <= 0) return false;

            // Le bloc signature commence par XY (2 chars)
            String prefixPlusData = payloadComplet.substring(0, usIdx + 3); // Inclut US + "XY"
            String signatureSoumise = payloadComplet.substring(usIdx + 3);

            // On retire le '=' final pour la comparaison si présent
            if (signatureSoumise.endsWith("=")) {
                signatureSoumise = signatureSoumise.substring(0, signatureSoumise.length() - 1);
            }

            Signature sig = Signature.getInstance(SIGN_ALGO);
            sig.initVerify(publicKey);
            sig.update(prefixPlusData.getBytes(StandardCharsets.UTF_8));
            
            byte[] sigBytes = decodeBase32(signatureSoumise);
            byte[] derSignature = rawToDer(sigBytes);
            
            return sig.verify(derSignature);
        } catch (Exception e) {
            LOG.warnf("Erreur vérification DataMatrix ECDSA : %s", e.getMessage());
            return false;
        }
    }

    private byte[] rawToDer(byte[] raw) throws Exception {
        if (raw.length != 64) return raw; // On laisse tel quel si ce n'est pas du P-256 RAW
        
        byte[] r = new byte[32];
        byte[] s = new byte[32];
        System.arraycopy(raw, 0, r, 0, 32);
        System.arraycopy(raw, 32, s, 0, 32);
        
        // On nettoie les bytes de signe si nécessaire pour ASN.1
        return encodeDer(r, s);
    }

    private byte[] encodeDer(byte[] r, byte[] s) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x30); // Sequence
        byte[] rDer = encodeInteger(r);
        byte[] sDer = encodeInteger(s);
        baos.write(rDer.length + sDer.length);
        baos.write(rDer);
        baos.write(sDer);
        return baos.toByteArray();
    }

    private byte[] encodeInteger(byte[] val) throws Exception {
        int start = 0;
        while (start < val.length && val[start] == 0) start++;
        int len = val.length - start;
        boolean extraByte = (val[start] & 0x80) != 0;
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x02); // Integer
        baos.write(extraByte ? len + 1 : len);
        if (extraByte) baos.write(0);
        baos.write(val, start, len);
        return baos.toByteArray();
    }

    private String normalizeDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return "";
        // Tente de transformer "15 Juin 1990" en "15/06/1990"
        String cleaned = dateStr.toLowerCase().trim();
        Map<String, String> months = Map.ofEntries(
            Map.entry("janvier", "01"), Map.entry("février", "02"), Map.entry("mars", "03"),
            Map.entry("avril", "04"), Map.entry("mai", "05"), Map.entry("juin", "06"),
            Map.entry("juillet", "07"), Map.entry("août", "08"), Map.entry("septembre", "09"),
            Map.entry("octobre", "10"), Map.entry("novembre", "11"), Map.entry("décembre", "12"),
            Map.entry("jan", "01"), Map.entry("fev", "02"), Map.entry("mar", "03"),
            Map.entry("avr", "04"), Map.entry("jun", "06"), Map.entry("jul", "07"),
            Map.entry("aug", "08"), Map.entry("sep", "09"), Map.entry("oct", "10"),
            Map.entry("nov", "11"), Map.entry("dec", "12")
        );

        for (Map.Entry<String, String> entry : months.entrySet()) {
            if (cleaned.contains(entry.getKey())) {
                // On extrait les chiffres (jour et année)
                String digits = cleaned.replaceAll("[^0-9]", " ").trim();
                String[] parts = digits.split("\\s+");
                if (parts.length >= 2) {
                    String day = parts[0].length() == 1 ? "0" + parts[0] : parts[0];
                    String year = parts[parts.length - 1];
                    return day + "/" + entry.getValue() + "/" + year;
                }
            }
        }
        return dateStr; // Retourne tel quel si non reconnu
    }

    /**
     * Parse le payload d'un DataMatrix 2D-Doc.
     */
    public Map<String, String> parseDatamatrixPayload(String payload) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        if (payload == null || payload.length() < 22) return result;

        // Header (Info de base)
        result.put("VERSION", payload.substring(2, 4));
        result.put("AUTHORITY", payload.substring(4, 8));
        result.put("CERT_ID", payload.substring(8, 12));
        
        // Extraction des données après le header
        int sigIdx = payload.lastIndexOf("XY");
        String dataPart = sigIdx > 22 ? payload.substring(22, sigIdx) : (payload.length() > 22 ? payload.substring(22) : "");
        
        if (!dataPart.isEmpty()) {
            String[] fields = dataPart.split(String.valueOf(US));
            for (String field : fields) {
                if (field.length() > 2) {
                    String tag = field.substring(0, 2);
                    String value = field.substring(2);
                    result.put(tag, value);
                }
            }
        }
        
        if (sigIdx > 0 && sigIdx < payload.length() - 2) {
            result.put("SIGNATURE", payload.substring(sigIdx + 2));
        }

        return result;
    }

    // -----------------------------------------------
    // UTILITAIRES ENCODAGE
    // -----------------------------------------------

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private String encodeBase32(byte[] data) {
        char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
        StringBuilder sb = new StringBuilder();
        int bitBuffer = 0;
        int bitCount = 0;
        for (byte b : data) {
            bitBuffer = (bitBuffer << 8) | (b & 0xFF);
            bitCount += 8;
            while (bitCount >= 5) {
                sb.append(alphabet[(bitBuffer >> (bitCount - 5)) & 31]);
                bitCount -= 5;
            }
        }
        if (bitCount > 0) {
            sb.append(alphabet[(bitBuffer << (5 - bitCount)) & 31]);
        }
        // Padding
        while (sb.length() % 8 != 0) sb.append('=');
        return sb.toString();
    }

    public byte[] decodeBase32(String base32) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        base32 = base32.toUpperCase().replaceAll("[^A-Z2-7]", "");
        int len = base32.length();
        int outLen = (len * 5) / 8;
        byte[] out = new byte[outLen];
        int buffer = 0;
        int bitsLeft = 0;
        int outIdx = 0;
        for (int i = 0; i < len; i++) {
            int val = alphabet.indexOf(base32.charAt(i));
            if (val == -1) continue;
            buffer <<= 5;
            buffer |= val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                if (outIdx < outLen) {
                    out[outIdx++] = (byte) (buffer >> (bitsLeft - 8));
                }
                bitsLeft -= 8;
            }
        }
        return out;
    }

    // -----------------------------------------------
    // UTILITAIRES
    // -----------------------------------------------

    private String encodeBase32NoPadding(byte[] data) {
        char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
        StringBuilder sb = new StringBuilder();
        int bitBuffer = 0;
        int bitCount = 0;
        for (byte b : data) {
            bitBuffer = (bitBuffer << 8) | (b & 0xFF);
            bitCount += 8;
            while (bitCount >= 5) {
                sb.append(alphabet[(bitBuffer >> (bitCount - 5)) & 31]);
                bitCount -= 5;
            }
        }
        if (bitCount > 0) {
            sb.append(alphabet[(bitBuffer << (5 - bitCount)) & 31]);
        }
        return sb.toString();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /** Comparaison en temps constant pour éviter les timing attacks */
    private static class MessageDigestComparer {
        static boolean isEqual(String a, String b) {
            if (a.length() != b.length()) return false;
            int result = 0;
            for (int i = 0; i < a.length(); i++) {
                result |= a.charAt(i) ^ b.charAt(i);
            }
            return result == 0;
        }
    }
}
