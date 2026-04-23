package com.cev;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DocumentResourceTest {

    // -----------------------------------------------
    // Templates
    // -----------------------------------------------

    @Test
    @org.junit.jupiter.api.Order(1)
    void listerTemplates_retourneListeNonVide() {
        given()
            .when().get("/api/v1/templates")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$.size()", greaterThanOrEqualTo(1));
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void getTemplate_codeConnu_retourneTemplate() {
        given()
            .when().get("/api/v1/templates/CERT-REUSSITE")
            .then()
                .statusCode(200)
                .body("code", equalTo("CERT-REUSSITE"))
                .body("actif", equalTo(true));
    }

    @Test
    void getTemplate_codeInconnu_retourne404() {
        given()
            .when().get("/api/v1/templates/INEXISTANT")
            .then()
                .statusCode(404);
    }

    // -----------------------------------------------
    // Emission document
    // -----------------------------------------------

    @Test
    @org.junit.jupiter.api.Order(3)
    void emettre_donneesValides_retourne201AvecReference() {
        Map<String, Object> request = Map.of(
            "templateCode",       "CERT-REUSSITE",
            "beneficiaireNom",    "KONÉ",
            "beneficiairePrenom", "Amara",
            "beneficiaireEmail",  "amara.kone@test.ci",
            "sourceDonnees",      "FORMULAIRE",
            "envoyerEmail",       false,
            "donnees", Map.of(
                "intitule_formation", "Gestion de projets numériques",
                "mention",            "Très bien"
            )
        );

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .header("X-User-Id", "AGENT-TEST")
            .when().post("/api/v1/documents/emettre")
            .then()
                .statusCode(201)
                .body("reference",      notNullValue())
                .body("statut",         equalTo("SIGNE"))
                .body("hashSignature",  notNullValue())
                .body("pdfUrl",         notNullValue());
    }

    @Test
    void emettre_templateInexistant_retourne404() {
        Map<String, Object> request = Map.of(
            "templateCode",    "INEXISTANT",
            "beneficiaireNom", "Test",
            "sourceDonnees",   "FORMULAIRE",
            "donnees",         Map.of()
        );

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when().post("/api/v1/documents/emettre")
            .then()
                .statusCode(404);
    }

    @Test
    void emettre_nomManquant_retourne400() {
        Map<String, Object> request = Map.of(
            "templateCode",  "CERT-REUSSITE",
            "sourceDonnees", "FORMULAIRE",
            "donnees",       Map.of()
        );

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when().post("/api/v1/documents/emettre")
            .then()
                .statusCode(400);
    }

    // -----------------------------------------------
    // Vérification
    // -----------------------------------------------

    @Test
    void verification_parametresManquants_retourne400() {
        given()
            .when().get("/api/v1/verification")
            .then()
                .statusCode(400);
    }

    @Test
    void verification_referenceInconnue_retourneInvalide() {
        given()
            .queryParam("ref",  "CERT-2099-INCONNU")
            .queryParam("hash", "aabbccddeeff00112233445566778899")
            .when().get("/api/v1/verification")
            .then()
                .statusCode(422)
                .body("valide", equalTo(false));
    }

    // -----------------------------------------------
    // Dashboard
    // -----------------------------------------------

    @Test
    void dashboard_stats_retourneCompteurs() {
        given()
            .when().get("/api/v1/dashboard/stats")
            .then()
                .statusCode(200)
                .body("totalDocuments",      greaterThanOrEqualTo(0))
                .body("documentsSignes",     greaterThanOrEqualTo(0))
                .body("documentsBrouillons", greaterThanOrEqualTo(0));
    }
}
