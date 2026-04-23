package com.cev;

import com.cev.model.TypeDocument;
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

    private static final String REF         = "CERT-2025-ABCD1234";
    private static final String NOM         = "KONÉ";
    private static final String PRENOM      = "Amara";
    private static final TypeDocument TYPE  = TypeDocument.CERTIFICAT;
    private static final LocalDate DATE     = LocalDate.of(2025, 6, 15);
    private static final char RS = '\u001E';

    // -----------------------------------------------
    // Tests signature HMAC
    // -----------------------------------------------

    @Test
    void signerTexte_retourneHashNonVide() {
        String hash = cevService.signerTexte("Hello World");
        assertNotNull(hash);
        assertFalse(hash.isBlank());
        assertEquals(64, hash.length(), "HMAC-SHA256 doit produire 64 caractères hex");
    }

    @Test
    void signerTexte_memesEntrees_memeSortie() {
        String hash1 = cevService.signerTexte("DataMatrix Data");
        String hash2 = cevService.signerTexte("DataMatrix Data");
        assertEquals(hash1, hash2, "La signature doit être déterministe");
    }

    // -----------------------------------------------
    // Tests vérification
    // -----------------------------------------------

    @Test
    void verifierDocument_payloadCorrect_retourneTrue() {
        String payload = cevService.buildFinalPayload(REF, NOM, PRENOM, TYPE, DATE, null, null);
        assertTrue(cevService.verifierDocument(payload));
    }

    @Test
    void verifierDocument_payloadFalsifie_retourneFalse() {
        String payload = cevService.buildFinalPayload(REF, NOM, PRENOM, TYPE, DATE, null, null);
        String payloadFaux = payload.replace(NOM, "AUTRE");
        assertFalse(cevService.verifierDocument(payloadFaux));
    }

    // -----------------------------------------------
    // Tests DataMatrix
    // -----------------------------------------------

    @Test
    void genererDatamatrixBase64_retourneImageNonVide() {
        String payload = cevService.buildFinalPayload(REF, NOM, PRENOM, TYPE, DATE, null, null);
        String base64  = cevService.genererDatamatrixBase64(payload);

        assertNotNull(base64);
        assertFalse(base64.isBlank());
        // Vérifier que c'est du base64 valide
        assertDoesNotThrow(() -> java.util.Base64.getDecoder().decode(base64));
    }

    @Test
    void genererDatamatrixBytes_retournePngValide() {
        String payload = cevService.buildFinalPayload(REF, NOM, PRENOM, TYPE, DATE, null, null);
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
        String payload = cevService.buildFinalPayload(REF, NOM, PRENOM, TYPE, DATE, null, null);

        Map<String, String> parsed = cevService.parseDatamatrixPayload(payload);

        assertEquals(REF,    parsed.get("01"));
        assertEquals(NOM,    parsed.get("02"));
        assertEquals(PRENOM.toUpperCase(), parsed.get("03"));
        assertEquals("04",   parsed.get("VERSION"));
    }

    @Test
    void parseDatamatrixPayload_payloadVide_retourneMapVide() {
        Map<String, String> parsed = cevService.parseDatamatrixPayload("");
        assertTrue(parsed.isEmpty());
    }
}
