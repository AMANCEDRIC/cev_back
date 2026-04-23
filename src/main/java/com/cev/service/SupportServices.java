package com.cev.service;

import com.cev.model.DocumentTemplate;
import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import io.minio.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import com.cev.model.DocumentEmis;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

// =====================================================
// TEMPLATE SERVICE — Remplissage Qute-like
// =====================================================

@ApplicationScoped
class TemplateService {

    private static final Logger LOG = Logger.getLogger(TemplateService.class);

    /**
     * Remplace les variables {cle} dans le contenu Qute par les valeurs données.
     * Pour un rendu avancé, Qute natif peut être utilisé avec EngineBuilder.
     */
    public String remplir(DocumentTemplate template, Map<String, Object> donnees) {
        String contenu = template.contenuQute;
        for (Map.Entry<String, Object> entry : donnees.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String valeur = entry.getValue() != null ? entry.getValue().toString() : "";
            contenu = contenu.replace(placeholder, valeur);
        }
        return contenu;
    }
}

// =====================================================
// PDF SERVICE — Génération PDF avec iText7
// =====================================================

@ApplicationScoped
class PdfService {

    private static final Logger LOG = Logger.getLogger(PdfService.class);

    public byte[] genererPdf(String html, String reference) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ConverterProperties props = new ConverterProperties();
            props.setCharset("UTF-8");
            HtmlConverter.convertToPdf(html, baos, props);
            LOG.infof("PDF généré pour document : %s (%d bytes)", reference, baos.size());
            return baos.toByteArray();
        } catch (Exception e) {
            LOG.errorf("Erreur génération PDF [%s] : %s", reference, e.getMessage());
            throw new RuntimeException("Erreur génération PDF", e);
        }
    }
}

// =====================================================
// STORAGE SERVICE — MinIO / S3
// =====================================================

@ApplicationScoped
class StorageService {

    private static final Logger LOG = Logger.getLogger(StorageService.class);

    @ConfigProperty(name = "cev.storage.endpoint")
    String endpoint;

    @ConfigProperty(name = "cev.storage.access-key")
    String accessKey;

    @ConfigProperty(name = "cev.storage.secret-key")
    String secretKey;

    @ConfigProperty(name = "cev.storage.bucket")
    String bucket;

    private MinioClient getClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    public String stocker(byte[] contenu, String nomFichier) {
        try {
            MinioClient client = getClient();
            // Créer le bucket s'il n'existe pas
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(nomFichier)
                    .stream(new ByteArrayInputStream(contenu), contenu.length, -1)
                    .contentType("application/pdf")
                    .build());
            LOG.infof("PDF stocké : %s/%s", bucket, nomFichier);
            return bucket + "/" + nomFichier;
        } catch (Exception e) {
            LOG.errorf("Erreur stockage MinIO : %s", e.getMessage());
            throw new RuntimeException("Erreur stockage document", e);
        }
    }

    public byte[] recuperer(String path) {
        try {
            String objet = path.contains("/") ? path.substring(path.indexOf('/') + 1) : path;
            MinioClient client = getClient();
            try (var stream = client.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objet)
                    .build())) {
                return stream.readAllBytes();
            }
        } catch (Exception e) {
            LOG.errorf("Erreur récupération MinIO : %s", e.getMessage());
            throw new RuntimeException("Erreur récupération PDF", e);
        }
    }
}

// =====================================================
// MAIL SERVICE — Envoi email avec pièce jointe PDF
// =====================================================

@ApplicationScoped
class MailService {

    private static final Logger LOG = Logger.getLogger(MailService.class);

    @Inject
    Mailer mailer;

    @ConfigProperty(name = "cev.organisation.nom", defaultValue = "CEV")
    String orgNom;

    public void envoyerDocument(DocumentEmis doc, byte[] pdfBytes) {
        try {
            String sujet = "[" + orgNom + "] Votre document : " + doc.reference;
            String corps = construireCorpsEmail(doc);

            mailer.send(Mail.withHtml(doc.beneficiaireEmail, sujet, corps)
                    .addAttachment(
                            doc.pdfNomFichier != null ? doc.pdfNomFichier : doc.reference + ".pdf",
                            pdfBytes,
                            "application/pdf"
                    ));

            LOG.infof("Email envoyé : %s → %s", doc.reference, doc.beneficiaireEmail);
        } catch (Exception e) {
            LOG.errorf("Erreur envoi email [%s] : %s", doc.reference, e.getMessage());
            throw new RuntimeException("Erreur envoi email", e);
        }
    }

    private String construireCorpsEmail(DocumentEmis doc) {
        return """
                <html><body style="font-family: Arial, sans-serif; padding: 20px;">
                <h2 style="color: #1a2744;">%s</h2>
                <p>Bonjour %s %s,</p>
                <p>Veuillez trouver ci-joint votre document officiel <strong>%s</strong>.</p>
                <table style="border-collapse: collapse; margin: 16px 0;">
                    <tr><td style="padding:4px 12px;color:#666">Référence</td>
                        <td style="padding:4px 12px;font-weight:bold">%s</td></tr>
                    <tr><td style="padding:4px 12px;color:#666">Type</td>
                        <td style="padding:4px 12px">%s</td></tr>
                    <tr><td style="padding:4px 12px;color:#666">Date d'émission</td>
                        <td style="padding:4px 12px">%s</td></tr>
                </table>
                <p style="color:#888;font-size:12px;">Ce document est signé électroniquement par le système CEV.
                Vous pouvez vérifier son authenticité en scannant le DataMatrix imprimé sur le document.</p>
                </body></html>
                """.formatted(
                orgNom,
                doc.beneficiairePrenom != null ? doc.beneficiairePrenom : "",
                doc.beneficiaireNom,
                doc.typeDocument.name().toLowerCase(),
                doc.reference,
                doc.typeDocument,
                doc.dateEmission
        );
    }
}

// =====================================================
// EXTERNAL DATA SERVICE — Appel API externe
// =====================================================

@ApplicationScoped
class ExternalDataService {

    private static final Logger LOG = Logger.getLogger(ExternalDataService.class);

    @Inject
    @RestClient
    ExternalDataClient client;

    public Map<String, Object> recuperer(String referenceExterne) {
        try {
            return client.getDonnees(referenceExterne);
        } catch (Exception e) {
            LOG.warnf("API externe indisponible pour ref=%s : %s", referenceExterne, e.getMessage());
            return Map.of();
        }
    }
}

// =====================================================
// REST CLIENT — Interface API externe
// =====================================================

@RegisterRestClient(configKey = "com.cev.service.ExternalDataClient")
@Path("/api")
interface ExternalDataClient {

    @GET
    @Path("/donnees/{reference}")
    Map<String, Object> getDonnees(@PathParam("reference") String reference);
}
