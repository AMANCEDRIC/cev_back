package com.cev.api;

import com.cev.dto.DashboardStats;
import com.cev.model.DocumentEmis;
import com.cev.model.DocumentVerification;
import com.cev.model.StatutDocument;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDate;

/**
 * API REST — Tableau de bord et statistiques CEV
 * Base path : /api/v1/dashboard
 */
@Path("/api/v1/dashboard")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Dashboard", description = "Statistiques et tableau de bord")
public class DashboardResource {

    @GET
    @Path("/stats")
    @Operation(summary = "Statistiques globales du système CEV")
    public DashboardStats stats() {
        DashboardStats s = new DashboardStats();
        s.totalDocuments      = DocumentEmis.count();
        s.documentsSignes     = DocumentEmis.countByStatut(StatutDocument.SIGNE);
        s.documentsBrouillons = DocumentEmis.countByStatut(StatutDocument.BROUILLON);
        s.documentsEnvoyes    = DocumentEmis.countByStatut(StatutDocument.ENVOYE);
        s.documentsRevoques   = DocumentEmis.countByStatut(StatutDocument.REVOQUE);
        s.verificationsAujourdhui = DocumentVerification.count(
            "DATE(verifieLe) = ?1", LocalDate.now()
        );
        return s;
    }
}
