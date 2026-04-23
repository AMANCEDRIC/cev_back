package com.cev.api;

import com.cev.dto.VerificationResponse;
import com.cev.service.DocumentService;
import com.cev.service.CevService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

/**
 * API REST — Vérification publique des documents CEV
 *
 * Ces endpoints sont publics (pas d'authentification requise)
 * pour permettre à n'importe qui de vérifier un document scanné.
 *
 * Base path : /api/v1/verification
 */
@Path("/api/v1/verification")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Vérification", description = "Vérification publique de l'authenticité des documents")
public class VerificationResource {

    @Inject DocumentService documentService;
    @Inject CevService cevService;

    @Context HttpHeaders headers;

    // -----------------------------------------------
    // GET /api/v1/verification?ref=CERT-2025-XXXX&hash=abc...
    // Vérification rapide via URL (lien dans le DataMatrix)
    // -----------------------------------------------
    @GET
    @Operation(
        summary = "Vérifier un document (GET — depuis lien DataMatrix)",
        description = "Endpoint appelé automatiquement quand un utilisateur scanne le DataMatrix. " +
                      "Le QR/DataMatrix contient l'URL complète avec ref et hash en paramètres."
    )
    public Response verifierGet(
            @QueryParam("ref")  String reference,
            @QueryParam("hash") String hash
    ) {
        if (reference == null || reference.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Paramètre 'ref' manquant")).build();
        }
        if (hash == null || hash.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Paramètre 'hash' manquant")).build();
        }

        VerificationResponse result = documentService.verifier(reference, hash);
        int status = result.valide ? 200 : 422;
        return Response.status(status).entity(result).build();
    }

    // -----------------------------------------------
    // POST /api/v1/verification
    // Vérification via payload DataMatrix complet (scanné)
    // -----------------------------------------------
    @POST
    @Operation(
        summary = "Vérifier un document (POST — payload DataMatrix complet)",
        description = "Soumet le contenu texte complet scanné depuis un DataMatrix pour vérification."
    )
    public Response verifierPost(Map<String, String> body) {
        if (body == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Corps de la requête manquant")).build();
        }

        String reference = body.get("reference");
        String hash      = body.get("hash");
        String payload   = body.get("datamatrixPayload");

        // Si payload brut fourni, on le parse
        if ((reference == null || hash == null) && payload != null) {
            Map<String, String> parsed = cevService.parseDatamatrixPayload(payload);
            reference = parsed.getOrDefault("REF", reference);
            hash      = parsed.getOrDefault("HSH", hash);
        }

        if (reference == null || hash == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Référence et hash requis")).build();
        }

        VerificationResponse result = documentService.verifier(reference, hash);
        int status = result.valide ? 200 : 422;
        return Response.status(status).entity(result).build();
    }

    // -----------------------------------------------
    // GET /api/v1/verification/{reference}/datamatrix
    // Obtenir l'image DataMatrix d'un document (PNG)
    // -----------------------------------------------
    @GET
    @Path("/{reference}/datamatrix")
    @Produces("image/png")
    @Operation(
        summary = "Obtenir l'image DataMatrix d'un document",
        description = "Retourne l'image PNG du DataMatrix pour affichage ou impression."
    )
    public Response getDatamatrixImage(
            @PathParam("reference") String reference,
            @QueryParam("taille")   @DefaultValue("150") int taille
    ) {
        // Récupérer le document pour avoir son payload
        try {
            var doc = documentService.getByReference(reference);
            if (doc.datamatrixPayload == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "DataMatrix non disponible pour ce document")).build();
            }

            int tailleSecurisee = Math.min(Math.max(taille, 80), 400);
            byte[] image = cevService.genererDatamatrixBytes(doc.datamatrixPayload, tailleSecurisee);

            return Response.ok(image)
                    .header("Content-Type", "image/png")
                    .header("Cache-Control", "max-age=86400")
                    .build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Document introuvable : " + reference)).build();
        }
    }
}
