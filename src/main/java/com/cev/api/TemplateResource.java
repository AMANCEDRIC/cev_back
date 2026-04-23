package com.cev.api;

import com.cev.dto.TemplateRequest;
import com.cev.dto.TemplateResponse;
import com.cev.model.DocumentTemplate;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * API REST — Gestion des templates de documents
 * Base path : /api/v1/templates
 */
@Path("/api/v1/templates")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Templates", description = "Gestion des modèles de documents")
public class TemplateResource {

    // -----------------------------------------------
    // GET /api/v1/templates
    // -----------------------------------------------
    @GET
    @Operation(summary = "Lister tous les templates actifs")
    public List<TemplateResponse> lister() {
        return DocumentTemplate.findActifs().stream()
                .map(this::toResponse)
                .toList();
    }

    // -----------------------------------------------
    // GET /api/v1/templates/{code}
    // -----------------------------------------------
    @GET
    @Path("/{code}")
    @Operation(summary = "Obtenir un template par son code")
    public Response getByCode(@PathParam("code") String code) {
        DocumentTemplate t = DocumentTemplate.findByCode(code);
        if (t == null) return Response.status(404).entity("Template introuvable : " + code).build();
        return Response.ok(toResponse(t)).build();
    }

    // -----------------------------------------------
    // POST /api/v1/templates
    // -----------------------------------------------
    @POST
    @Transactional
    @Operation(summary = "Créer un nouveau template")
    public Response creer(@Valid TemplateRequest req) {
        if (DocumentTemplate.findByCode(req.code) != null) {
            return Response.status(409)
                    .entity("Un template avec le code '" + req.code + "' existe déjà").build();
        }
        DocumentTemplate t = new DocumentTemplate();
        t.code          = req.code;
        t.libelle       = req.libelle;
        t.typeDocument  = req.typeDocument;
        t.description   = req.description;
        t.contenuQute   = req.contenuQute;
        t.champsJson    = req.champsJson != null ? req.champsJson : "[]";
        t.persist();
        return Response.status(201).entity(toResponse(t)).build();
    }

    // -----------------------------------------------
    // PUT /api/v1/templates/{code}
    // -----------------------------------------------
    @PUT
    @Path("/{code}")
    @Transactional
    @Operation(summary = "Mettre à jour un template existant")
    public Response modifier(@PathParam("code") String code, @Valid TemplateRequest req) {
        DocumentTemplate t = DocumentTemplate.findByCode(code);
        if (t == null) return Response.status(404).build();

        t.libelle      = req.libelle;
        t.description  = req.description;
        t.contenuQute  = req.contenuQute;
        t.champsJson   = req.champsJson;
        t.modifieLe    = LocalDateTime.now();
        t.persist();
        return Response.ok(toResponse(t)).build();
    }

    // -----------------------------------------------
    // DELETE /api/v1/templates/{code} (désactivation)
    // -----------------------------------------------
    @DELETE
    @Path("/{code}")
    @Transactional
    @Operation(summary = "Désactiver un template (soft delete)")
    public Response desactiver(@PathParam("code") String code) {
        DocumentTemplate t = DocumentTemplate.findByCode(code);
        if (t == null) return Response.status(404).build();
        t.actif = false;
        t.persist();
        return Response.noContent().build();
    }

    private TemplateResponse toResponse(DocumentTemplate t) {
        TemplateResponse r = new TemplateResponse();
        r.id           = t.id;
        r.code         = t.code;
        r.libelle      = t.libelle;
        r.typeDocument = t.typeDocument;
        r.description  = t.description;
        r.champsJson   = t.champsJson;
        r.actif        = t.actif;
        r.creeLe       = t.creeLe;
        return r;
    }
}
