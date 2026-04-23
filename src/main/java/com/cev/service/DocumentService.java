package com.cev.service;

import com.cev.dto.DocumentRequest;
import com.cev.dto.DocumentResponse;
import com.cev.dto.DocumentSummary;
import com.cev.dto.VerificationResponse;
import com.cev.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service principal orchestrant le cycle de vie complet d'un document :
 *  Création → Remplissage template → Signature CEV → Génération PDF → Stockage → Envoi
 */
@ApplicationScoped
public class DocumentService {

    private static final Logger LOG = Logger.getLogger(DocumentService.class);

    @Inject CevService cevService;
    @Inject TemplateService templateService;
    @Inject PdfService pdfService;
    @Inject StorageService storageService;
    @Inject MailService mailService;
    @Inject ExternalDataService externalDataService;
    @Inject ObjectMapper objectMapper;

    // -----------------------------------------------
    // CRÉATION ET ÉMISSION D'UN DOCUMENT
    // -----------------------------------------------

    /**
     * Flux complet : crée, remplit, signe et optionnellement envoie le document.
     */
    @Transactional
    public DocumentResponse emettre(DocumentRequest req, String creePar) {
        LOG.infof("Émission document : template=%s, bénéficiaire=%s %s",
                req.templateCode, req.beneficiairePrenom, req.beneficiaireNom);

        // 1. Charger le template
        DocumentTemplate template = DocumentTemplate.findByCode(req.templateCode);
        if (template == null || !template.actif) {
            throw new NotFoundException("Template introuvable ou inactif : " + req.templateCode);
        }

        // 2. Enrichir les données selon la source
        Map<String, Object> donnees = enrichirDonnees(req);

        // 3. Créer l'entité document
        DocumentEmis doc = new DocumentEmis();
        doc.reference       = genererReference(template.typeDocument);
        doc.template        = template;
        doc.typeDocument    = template.typeDocument;
        doc.sourceDonnees   = req.sourceDonnees;
        doc.beneficiaireNom    = req.beneficiaireNom;
        doc.beneficiairePrenom = req.beneficiairePrenom;
        doc.beneficiaireEmail  = req.beneficiaireEmail;
        doc.dateExpiration  = req.dateExpiration;
        doc.donneesJson     = toJson(donnees);
        doc.statut          = StatutDocument.EN_ATTENTE_SIGNATURE;
        doc.creePar         = creePar;
        doc.persist();

        // Ajouter donnees système dans la map pour le template
        donnees.put("reference", doc.reference);
        donnees.put("date_emission", doc.dateEmission.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        donnees.put("prenom", req.beneficiairePrenom);
        donnees.put("nom", req.beneficiaireNom);

        // 4. SIGNATURE CEV (automatique)
        String payload = cevService.buildFinalPayload(
                doc.reference,
                doc.beneficiaireNom,
                doc.beneficiairePrenom,
                doc.typeDocument,
                doc.dateEmission,
                doc.dateExpiration,
                donnees
        );
        
        // On récupère le hash pour la base de données
        int sigIdx = payload.lastIndexOf("XY");
        String dataPart = sigIdx > 0 ? payload.substring(0, sigIdx) : payload;
        String hash = cevService.signerTexte(dataPart);

        doc.hashSignature    = hash;
        doc.datamatrixPayload = payload;
        doc.signeLe          = LocalDateTime.now();
        doc.statut           = StatutDocument.SIGNE;

        // 5. Générer l'image DataMatrix (base64 pour l'injecter dans le template)
        String datamatrixBase64 = cevService.genererDatamatrixBase64(payload, 150);
        donnees.put("datamatrix_placeholder",
                "<img src='data:image/png;base64," + datamatrixBase64 + "' width='120' height='120' />");

        // 6. Remplir le template Qute → HTML
        String htmlDoc = templateService.remplir(template, donnees);

        // 7. Générer le PDF
        byte[] pdfBytes = pdfService.genererPdf(htmlDoc, doc.reference);

        // 8. Stocker le PDF
        String pdfPath = storageService.stocker(pdfBytes, doc.reference + ".pdf");
        doc.pdfPath       = pdfPath;
        doc.pdfNomFichier = doc.reference + ".pdf";

        doc.persist();

        // 9. Historique
        logHistorique(doc, "EMISSION", "Document émis et signé par CEV", creePar);

        // 10. Envoi email si demandé
        if (req.envoyerEmail && doc.beneficiaireEmail != null) {
            mailService.envoyerDocument(doc, pdfBytes);
            doc.statut = StatutDocument.ENVOYE;
            doc.persist();
            logHistorique(doc, "ENVOI_EMAIL", "Envoyé à " + doc.beneficiaireEmail, creePar);
        }

        LOG.infof("Document émis avec succès : %s (statut=%s)", doc.reference, doc.statut);
        return toResponse(doc);
    }

    // -----------------------------------------------
    // RÉCUPÉRATION
    // -----------------------------------------------

    public DocumentResponse getByReference(String reference) {
        DocumentEmis doc = DocumentEmis.findByReference(reference);
        if (doc == null) throw new NotFoundException("Document introuvable : " + reference);
        return toResponse(doc);
    }

    public List<DocumentSummary> lister(int page, int taille, StatutDocument statut) {
        List<DocumentEmis> docs;
        if (statut != null) {
            docs = DocumentEmis.find("statut", statut)
                    .page(page, taille).list();
        } else {
            docs = DocumentEmis.findAll()
                    .page(page, taille).list();
        }
        return docs.stream().map(this::toSummary).toList();
    }

    public byte[] telechargerPdf(String reference) {
        DocumentEmis doc = DocumentEmis.findByReference(reference);
        if (doc == null) throw new NotFoundException("Document introuvable : " + reference);
        if (doc.pdfPath == null) throw new IllegalStateException("PDF non disponible pour : " + reference);
        return storageService.recuperer(doc.pdfPath);
    }

    // -----------------------------------------------
    // VÉRIFICATION
    // -----------------------------------------------

    @Transactional
    public VerificationResponse verifier(String reference, String hashSoumis) {
        VerificationResponse resp = new VerificationResponse();
        resp.reference = reference;

        DocumentEmis doc = DocumentEmis.findByReference(reference);
        if (doc == null) {
            resp.valide  = false;
            resp.message = "Référence inconnue — document introuvable dans le registre CEV";
            return resp;
        }

        if (doc.statut == StatutDocument.REVOQUE) {
            resp.valide  = false;
            resp.message = "Document révoqué — ce document n'est plus valide";
            return resp;
        }

        // Pour 2D-Doc, on vérifie soit le hash directement (si fourni via API),
        // soit on recalcule à partir du payload complet si disponible.
        boolean valid = cevService.verifierDocument(hashSoumis); 
        
        // Note: Si hashSoumis n'est pas un payload complet mais juste le hash hex, 
        // cette méthode retournera false. Dans ce cas, on peut fallback sur la référence.
        if (!valid && doc.hashSignature != null) {
            valid = doc.hashSignature.equalsIgnoreCase(hashSoumis);
        }

        if (valid && doc.dateExpiration != null && LocalDate.now().isAfter(doc.dateExpiration)) {
            resp.valide  = false;
            resp.message = "Document expiré le " + doc.dateExpiration;
        } else {
            resp.valide   = valid;
            resp.message  = valid ? "Document authentique — signature CEV vérifiée" : "Signature invalide";
        }

        resp.beneficiaireNom    = doc.beneficiaireNom;
        resp.beneficiairePrenom = doc.beneficiairePrenom;
        resp.typeDocument        = doc.typeDocument;
        resp.dateEmission        = doc.dateEmission;
        resp.dateExpiration      = doc.dateExpiration;
        resp.signePar            = doc.signePar;
        resp.signeLe             = doc.signeLe;
        resp.statut              = doc.statut;

        // Traçabilité
        logVerification(reference, hashSoumis, valid);
        return resp;
    }

    // -----------------------------------------------
    // RÉVOCATION
    // -----------------------------------------------

    @Transactional
    public void revoquer(String reference, String motif, String par) {
        DocumentEmis doc = DocumentEmis.findByReference(reference);
        if (doc == null) throw new NotFoundException("Document introuvable : " + reference);
        doc.statut = StatutDocument.REVOQUE;
        doc.persist();
        logHistorique(doc, "REVOCATION", motif, par);
        LOG.infof("Document révoqué : %s par %s", reference, par);
    }

    // -----------------------------------------------
    // ENRICHISSEMENT DES DONNÉES
    // -----------------------------------------------

    private Map<String, Object> enrichirDonnees(DocumentRequest req) {
        Map<String, Object> donnees = new LinkedHashMap<>();

        // Données saisies manuellement
        if (req.donnees != null) {
            donnees.putAll(req.donnees);
        }

        // Enrichissement depuis API externe si demandé
        if (req.sourceDonnees == SourceDonnees.API_EXTERNE && req.referenceExterne != null) {
            try {
                Map<String, Object> extData = externalDataService.recuperer(req.referenceExterne);
                donnees.putAll(extData);
                LOG.infof("Données enrichies depuis API externe pour ref=%s", req.referenceExterne);
            } catch (Exception e) {
                LOG.warnf("Impossible d'enrichir depuis API externe : %s", e.getMessage());
            }
        }

        return donnees;
    }

    // -----------------------------------------------
    // UTILITAIRES
    // -----------------------------------------------

    private String genererReference(TypeDocument type) {
        String prefix = switch (type) {
            case CERTIFICAT  -> "CERT";
            case DIPLOME     -> "DIPL";
            case ATTESTATION -> "ATST";
            case FACTURE     -> "FACT";
            default          -> "DOC";
        };
        String annee = String.valueOf(LocalDate.now().getYear());
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return prefix + "-" + annee + "-" + suffix;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        try {
            if (json == null || json.isBlank()) return new HashMap<>();
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private void logHistorique(DocumentEmis doc, String action, String detail, String par) {
        com.cev.model.DocumentHistorique h = new com.cev.model.DocumentHistorique();
        h.documentId  = doc.id;
        h.action      = action;
        h.detail      = detail;
        h.effectuePar = par;
        h.persist();
    }

    private void logVerification(String ref, String hash, boolean result) {
        com.cev.model.DocumentVerification v = new com.cev.model.DocumentVerification();
        v.reference   = ref;
        v.hashSoumis  = hash;
        v.resultat    = result;
        v.persist();
    }

    // -----------------------------------------------
    // MAPPERS
    // -----------------------------------------------

    private DocumentResponse toResponse(DocumentEmis doc) {
        DocumentResponse r = new DocumentResponse();
        r.id                 = doc.id;
        r.reference          = doc.reference;
        r.templateCode       = doc.template != null ? doc.template.code : null;
        r.typeDocument       = doc.typeDocument;
        r.sourceDonnees      = doc.sourceDonnees;
        r.beneficiaireNom    = doc.beneficiaireNom;
        r.beneficiairePrenom = doc.beneficiairePrenom;
        r.beneficiaireEmail  = doc.beneficiaireEmail;
        r.statut             = doc.statut;
        r.hashSignature      = doc.hashSignature;
        r.datamatrixPayload  = doc.datamatrixPayload;
        r.signeLe            = doc.signeLe;
        r.signePar           = doc.signePar;
        r.pdfUrl             = doc.pdfPath != null ? "/api/v1/documents/" + doc.reference + "/pdf" : null;
        r.dateEmission       = doc.dateEmission;
        r.dateExpiration     = doc.dateExpiration;
        r.creeLe             = doc.creeLe;
        r.creePar            = doc.creePar;
        r.donnees            = fromJson(doc.donneesJson);
        return r;
    }

    private DocumentSummary toSummary(DocumentEmis doc) {
        DocumentSummary s = new DocumentSummary();
        s.id                 = doc.id;
        s.reference          = doc.reference;
        s.beneficiaireNom    = doc.beneficiaireNom;
        s.beneficiairePrenom = doc.beneficiairePrenom;
        s.typeDocument       = doc.typeDocument;
        s.statut             = doc.statut;
        s.dateEmission       = doc.dateEmission;
        s.creeLe             = doc.creeLe;
        s.estSigne           = doc.hashSignature != null;
        return s;
    }
}
