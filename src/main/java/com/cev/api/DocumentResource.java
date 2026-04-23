package com.cev.api;

import com.cev.dto.*;
import com.cev.model.StatutDocument;
import com.cev.service.DocumentService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

/**
 * API REST — Gestion des documents CEV
 *
 * Base path : /api/v1/documents
 */
@Path("/api/v1/documents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Documents", description = "Création, émission et gestion des documents signés")
public class DocumentResource {

    @Inject
    DocumentService documentService;

    @Context
    HttpHeaders headers;

    // -----------------------------------------------
    // POST /api/v1/documents/emettre
    // Créer + remplir + signer un document (flux complet)
    // -----------------------------------------------
    @POST
    @Path("/emettre")
    @Operation(
        summary = "Émettre un document signé (flux complet CEV)",
        description = "Crée un document depuis le template, remplit les champs automatiquement, " +
                      "signe avec le CEV (DataMatrix HMAC), génère le PDF et le stocke."
    )
    @APIResponse(responseCode = "201", description = "Document émis et signé avec succès",
                 content = @Content(schema = @Schema(implementation = DocumentResponse.class)))
    @APIResponse(responseCode = "400", description = "Données invalides")
    @APIResponse(responseCode = "404", description = "Template introuvable")
    public Response emettre(@Valid DocumentRequest request) {
        String creePar = headers.getHeaderString("X-User-Id");
        if (creePar == null) creePar = "AGENT-ANONYME";

        DocumentResponse doc = documentService.emettre(request, creePar);
        return Response.status(Response.Status.CREATED).entity(doc).build();
    }

    // -----------------------------------------------
    // GET /api/v1/documents
    // Lister les documents avec pagination et filtre statut
    // -----------------------------------------------
    @GET
    @Operation(summary = "Lister les documents", description = "Retourne la liste paginée des documents émis")
    public Response lister(
            @QueryParam("page")   @DefaultValue("0")    int page,
            @QueryParam("taille") @DefaultValue("20")   int taille,
            @QueryParam("statut")                       StatutDocument statut
    ) {
        List<DocumentSummary> docs = documentService.lister(page, taille, statut);
        return Response.ok(docs).build();
    }

    // -----------------------------------------------
    // GET /api/v1/documents/{reference}
    // Détail d'un document
    // -----------------------------------------------
    @GET
    @Path("/{reference}")
    @Operation(summary = "Obtenir le détail d'un document par sa référence")
    @APIResponse(responseCode = "200", description = "Document trouvé",
                 content = @Content(schema = @Schema(implementation = DocumentResponse.class)))
    @APIResponse(responseCode = "404", description = "Document introuvable")
    public Response getByReference(@PathParam("reference") String reference) {
        DocumentResponse doc = documentService.getByReference(reference);
        return Response.ok(doc).build();
    }

    // -----------------------------------------------
    // GET /api/v1/documents/{reference}/pdf
    // Télécharger le PDF signé
    // -----------------------------------------------
    @GET
    @Path("/{reference}/pdf")
    @Produces("application/pdf")
    @Operation(summary = "Télécharger le PDF signé d'un document")
    @APIResponse(responseCode = "200", description = "PDF du document")
    @APIResponse(responseCode = "404", description = "Document ou PDF introuvable")
    public Response telechargerPdf(@PathParam("reference") String reference) {
        byte[] pdf = documentService.telechargerPdf(reference);
        return Response.ok(pdf)
                .header("Content-Disposition", "attachment; filename=\"" + reference + ".pdf\"")
                .header("Content-Type", "application/pdf")
                .build();
    }

    // -----------------------------------------------
    // POST /api/v1/documents/{reference}/revoquer
    // Révoquer un document
    // -----------------------------------------------
    @POST
    @Path("/{reference}/revoquer")
    @Operation(summary = "Révoquer un document", description = "Invalide définitivement un document signé")
    @APIResponse(responseCode = "200", description = "Document révoqué")
    public Response revoquer(
            @PathParam("reference") String reference,
            Map<String, String> body
    ) {
        String motif = body != null ? body.getOrDefault("motif", "Révocation sans motif") : "Révocation";
        String par   = headers.getHeaderString("X-User-Id");
        if (par == null) par = "ADMIN";

        documentService.revoquer(reference, motif, par);
        return Response.ok(Map.of(
                "message", "Document révoqué avec succès",
                "reference", reference
        )).build();
    }
}
