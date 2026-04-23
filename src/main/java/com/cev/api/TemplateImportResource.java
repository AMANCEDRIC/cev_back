package com.cev.api;

import com.cev.dto.TemplateImportDetectRequest;
import com.cev.dto.TemplateImportDetectResponse;
import com.cev.dto.TemplateImportFieldsUpdateRequest;
import com.cev.dto.TemplateImportPublishRequest;
import com.cev.dto.TemplateImportResponse;
import com.cev.model.TemplateDetectMode;
import com.cev.service.TemplateImportService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Path("/api/v1/template-imports")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "TemplateImports", description = "Import PDF, détection de champs et publication de templates")
public class TemplateImportResource {

    @Inject
    TemplateImportService service;

    @jakarta.ws.rs.core.Context
    HttpHeaders headers;

    @POST
    @Consumes("application/pdf")
    @Operation(summary = "Uploader un template PDF brut")
    public Response uploadPdf(
            InputStream body,
            @HeaderParam("X-File-Name") String fileName,
            @QueryParam("nom") String nom,
            @QueryParam("detectMode") @DefaultValue("AUTO") TemplateDetectMode detectMode
    ) throws IOException {
        if (body == null) {
            return Response.status(400).entity(
                    java.util.Map.of("error", "Body PDF manquant (application/pdf)")
            ).build();
        }
        String user = headers.getHeaderString("X-User-Id");
        if (user == null || user.isBlank()) user = "IMPORT-ANONYME";
        TemplateImportResponse resp = service.uploadPdf(body.readAllBytes(), fileName, nom, detectMode, user);
        return Response.status(201).entity(resp).build();
    }

    @POST
    @Path("/{importId}/detect")
    @Operation(summary = "Détecter les champs template depuis PDF")
    public TemplateImportDetectResponse detect(
            @PathParam("importId") UUID importId,
            TemplateImportDetectRequest req
    ) {
        return service.detect(importId, req != null ? req.patterns : null);
    }

    @PUT
    @Path("/{importId}/fields")
    @Operation(summary = "Valider ou mapper manuellement les champs détectés")
    public TemplateImportDetectResponse updateFields(
            @PathParam("importId") UUID importId,
            TemplateImportFieldsUpdateRequest req
    ) {
        return service.updateFieldMappings(importId, req);
    }

    @POST
    @Path("/{importId}/publish")
    @Operation(summary = "Publier l'import en template standard utilisable par /documents/emettre")
    public Response publish(
            @PathParam("importId") UUID importId,
            @Valid TemplateImportPublishRequest req
    ) {
        TemplateImportResponse resp = service.publish(importId, req);
        return Response.status(201).entity(resp).build();
    }

    @GET
    @Path("/{importId}")
    @Operation(summary = "Récupérer un import par son identifiant")
    public TemplateImportResponse get(@PathParam("importId") UUID importId) {
        return service.get(importId);
    }

    @GET
    @Operation(summary = "Lister les imports")
    public List<TemplateImportResponse> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size
    ) {
        return service.list(page, size);
    }
}
