package com.cev.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

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
    private static final String HMAC_ALGO = "HmacSHA256";

    @ConfigProperty(name = "cev.signature.secret")
    String secretKey;

    @ConfigProperty(name = "cev.organisation.code", defaultValue = "CEV")
    String organisationCode;

    @ConfigProperty(name = "cev.document.base-url", defaultValue = "http://localhost:8080")
    String baseUrl;

    // -----------------------------------------------
    // 1. SIGNATURE HMAC-SHA256
    // -----------------------------------------------

    /**
     * Calcule la signature HMAC-SHA256 du document.
     *
     * @param reference  référence unique du document
     * @param nom        nom du bénéficiaire
     * @param prenom     prénom du bénéficiaire
     * @param type       type de document
     * @param dateEmission date d'émission
     * @return signature hexadécimale
     */
    public String signerDocument(String reference, String nom, String prenom,
                                  String type, LocalDate dateEmission) {
        try {
            String payload = buildSignaturePayload(reference, nom, prenom, type, dateEmission);
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGO);
            mac.init(keySpec);
            byte[] hashBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (Exception e) {
            LOG.errorf("Erreur signature HMAC : %s", e.getMessage());
            throw new RuntimeException("Erreur lors de la signature du document", e);
        }
    }

    private String buildSignaturePayload(String reference, String nom, String prenom,
                                          String type, LocalDate dateEmission) {
        return String.join("|",
                reference,
                nom.toUpperCase().trim(),
                prenom != null ? prenom.toUpperCase().trim() : "",
                type,
                dateEmission.toString(),
                organisationCode
        );
    }

    // -----------------------------------------------
    // 2. CONSTRUCTION DU PAYLOAD DATAMATRIX
    // -----------------------------------------------

    /**
     * Construit le contenu texte qui sera encodé dans le DataMatrix.
     * Format compact pour maximiser la densité d'information.
     */
    public String buildDatamatrixPayload(String reference, String nom, String prenom,
                                          String type, LocalDate dateEmission,
                                          LocalDate dateExpiration, String hash) {
        StringBuilder sb = new StringBuilder();
        sb.append("REF:").append(reference).append("\n");
        sb.append("NOM:").append(nom.toUpperCase().trim()).append("\n");
        if (prenom != null && !prenom.isBlank()) {
            sb.append("PRE:").append(prenom.toUpperCase().trim()).append("\n");
        }
        sb.append("TYP:").append(type).append("\n");
        sb.append("EMI:").append(dateEmission.toString()).append("\n");
        if (dateExpiration != null) {
            sb.append("EXP:").append(dateExpiration.toString()).append("\n");
        }
        sb.append("ORG:").append(organisationCode).append("\n");
        // Hash tronqué à 32 chars pour garder le DataMatrix lisible
        sb.append("HSH:").append(hash.substring(0, Math.min(32, hash.length()))).append("\n");
        sb.append("VER:").append(baseUrl).append("/api/v1/verification?ref=").append(reference);
        return sb.toString();
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
    public boolean verifierDocument(String reference, String nom, String prenom,
                                     String type, LocalDate dateEmission,
                                     String hashSoumis) {
        try {
            String hashAttendu = signerDocument(reference, nom, prenom, type, dateEmission);
            // Comparaison résistante aux timing attacks
            return MessageDigestComparer.isEqual(
                    hashAttendu.substring(0, Math.min(32, hashAttendu.length())),
                    hashSoumis.substring(0, Math.min(32, hashSoumis.length()))
            );
        } catch (Exception e) {
            LOG.warnf("Erreur vérification document %s : %s", reference, e.getMessage());
            return false;
        }
    }

    /**
     * Parse le payload d'un DataMatrix scanné et retourne une map clé/valeur.
     */
    public Map<String, String> parseDatamatrixPayload(String payload) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        if (payload == null || payload.isBlank()) return result;

        for (String line : payload.split("\n")) {
            int colonIdx = line.indexOf(':');
            if (colonIdx > 0) {
                String key = line.substring(0, colonIdx).trim();
                String value = line.substring(colonIdx + 1).trim();
                result.put(key, value);
            }
        }
        return result;
    }

    // -----------------------------------------------
    // UTILITAIRES
    // -----------------------------------------------

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
