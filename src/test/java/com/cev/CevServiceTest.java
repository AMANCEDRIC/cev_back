package com.cev;

import com.cev.service.CevService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class CevServiceTest {

    @Inject
    CevService cevService;

    private static final String REF     = "CERT-2025-ABCD1234";
    private static final String NOM     = "KONÉ";
    private static final String PRENOM  = "Amara";
    private static final String TYPE    = "CERTIFICAT";
    private static final LocalDate DATE = LocalDate.of(2025, 6, 15);

    // -----------------------------------------------
    // Tests signature HMAC
    // -----------------------------------------------

    @Test
    void signerDocument_retourneHashNonVide() {
        String hash = cevService.signerDocument(REF, NOM, PRENOM, TYPE, DATE);
        assertNotNull(hash);
        assertFalse(hash.isBlank());
        assertEquals(64, hash.length(), "HMAC-SHA256 doit produire 64 caractères hex");
    }

    @Test
    void signerDocument_memesEntrees_memeSortie() {
        String hash1 = cevService.signerDocument(REF, NOM, PRENOM, TYPE, DATE);
        String hash2 = cevService.signerDocument(REF, NOM, PRENOM, TYPE, DATE);
        assertEquals(hash1, hash2, "La signature doit être déterministe");
    }

    @Test
    void signerDocument_differentesEntrees_differentsSorties() {
        String hash1 = cevService.signerDocument(REF, NOM, PRENOM, TYPE, DATE);
        String hash2 = cevService.signerDocument("CERT-2025-AUTRE", NOM, PRENOM, TYPE, DATE);
        assertNotEquals(hash1, hash2);
    }

    // -----------------------------------------------
    // Tests vérification
    // -----------------------------------------------

    @Test
    void verifierDocument_hashCorrect_retourneTrue() {
        String hash = cevService.signerDocument(REF, NOM, PRENOM, TYPE, DATE);
        String hashTronque = hash.substring(0, 32);
        assertTrue(cevService.verifierDocument(REF, NOM, PRENOM, TYPE, DATE, hashTronque));
    }

    @Test
    void verifierDocument_hashFalse_retourneFalse() {
        assertFalse(cevService.verifierDocument(REF, NOM, PRENOM, TYPE, DATE, "hashfaux00000000000000000000000000"));
    }

    @Test
    void verifierDocument_nomModifie_retourneFalse() {
        String hash = cevService.signerDocument(REF, NOM, PRENOM, TYPE, DATE);
        assertFalse(cevService.verifierDocument(REF, "AUTRE", PRENOM, TYPE, DATE, hash.substring(0, 32)));
    }

    // -----------------------------------------------
    // Tests DataMatrix
    // -----------------------------------------------

    @Test
    void genererDatamatrixBase64_retourneImageNonVide() {
        String hash    = cevService.signerDocument(REF, NOM, PRENOM, TYPE, DATE);
        String payload = cevService.buildDatamatrixPayload(REF, NOM, PRENOM, TYPE, DATE, null, hash);
        String base64  = cevService.genererDatamatrixBase64(payload);

        assertNotNull(base64);
        assertFalse(base64.isBlank());
        // Vérifier que c'est du base64 valide
        assertDoesNotThrow(() -> java.util.Base64.getDecoder().decode(base64));
    }

    @Test
    void genererDatamatrixBytes_retournePngValide() {
        String hash    = cevService.signerDocument(REF, NOM, PRENOM, TYPE, DATE);
        String payload = cevService.buildDatamatrixPayload(REF, NOM, PRENOM, TYPE, DATE, null, hash);
        byte[] bytes   = cevService.genererDatamatrixBytes(payload, 150);

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
        // Vérifier magic bytes PNG : 0x89 0x50 0x4E 0x47
        assertEquals((byte) 0x89, bytes[0]);
        assertEquals((byte) 0x50, bytes[1]);
        assertEquals((byte) 0x4E, bytes[2]);
        assertEquals((byte) 0x47, bytes[3]);
    }

    // -----------------------------------------------
    // Tests parsing payload
    // -----------------------------------------------

    @Test
    void parseDatamatrixPayload_payloadComplet_parseCorrectement() {
        String hash    = cevService.signerDocument(REF, NOM, PRENOM, TYPE, DATE);
        String payload = cevService.buildDatamatrixPayload(REF, NOM, PRENOM, TYPE, DATE, null, hash);

        Map<String, String> parsed = cevService.parseDatamatrixPayload(payload);

        assertEquals(REF,    parsed.get("REF"));
        assertEquals(NOM,    parsed.get("NOM"));
        assertEquals(PRENOM.toUpperCase(), parsed.get("PRE"));
        assertEquals(TYPE,   parsed.get("TYP"));
        assertEquals(DATE.toString(), parsed.get("EMI"));
    }

    @Test
    void parseDatamatrixPayload_payloadVide_retourneMapVide() {
        Map<String, String> parsed = cevService.parseDatamatrixPayload("");
        assertTrue(parsed.isEmpty());
    }
}
