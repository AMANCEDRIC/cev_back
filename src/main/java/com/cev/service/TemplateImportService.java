package com.cev.service;

import com.cev.dto.TemplateImportDetectResponse;
import com.cev.dto.TemplateImportFieldDTO;
import com.cev.dto.TemplateImportFieldsUpdateRequest;
import com.cev.dto.TemplateImportPublishRequest;
import com.cev.dto.TemplateImportResponse;
import com.cev.model.DocumentTemplate;
import com.cev.model.TemplateDetectMode;
import com.cev.model.TemplateFieldSource;
import com.cev.model.TemplateImport;
import com.cev.model.TemplateImportField;
import com.cev.model.TemplateImportStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.text.PDFTextStripper;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class TemplateImportService {

    @ConfigProperty(name = "cev.template-import.storage-dir", defaultValue = "target/template-imports")
    String storageDir;

    @Inject
    ObjectMapper objectMapper;

    @Transactional
    public TemplateImportResponse uploadPdf(
            byte[] pdfBytes,
            String fileName,
            String nom,
            TemplateDetectMode detectMode,
            String createdBy
    ) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalStateException("Le fichier PDF est vide");
        }

        String safeName = normalizeFilename(fileName != null ? fileName : "template.pdf");
        String storageToken = UUID.randomUUID().toString();
        Path target = Path.of(storageDir, storageToken + "-" + safeName);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, pdfBytes);
        } catch (IOException e) {
            throw new RuntimeException("Impossible de stocker le fichier importé", e);
        }

        int pages = readPageCount(pdfBytes);
        TemplateImport imp = new TemplateImport();
        imp.nom = nom;
        imp.fileName = safeName;
        imp.storagePath = target.toString();
        imp.detectMode = detectMode != null ? detectMode : TemplateDetectMode.AUTO;
        imp.status = TemplateImportStatus.UPLOADED;
        imp.pages = pages;
        imp.createdBy = createdBy;
        imp.createdAt = LocalDateTime.now();
        imp.updatedAt = LocalDateTime.now();
        imp.persist();

        return toResponse(imp);
    }

    @Transactional
    public TemplateImportDetectResponse detect(UUID importId, List<String> customPatterns) {
        TemplateImport imp = requireImport(importId);
        byte[] bytes = readStoredPdf(imp.storagePath);

        Set<String> fieldNames = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();

        if (imp.detectMode == TemplateDetectMode.AUTO || imp.detectMode == TemplateDetectMode.TEXT) {
            fieldNames.addAll(detectFromText(bytes, customPatterns));
        }

        if (imp.detectMode == TemplateDetectMode.AUTO || imp.detectMode == TemplateDetectMode.ACROFORM) {
            fieldNames.addAll(detectFromAcroForm(bytes));
        }

        TemplateImportField.delete("importId", importId);
        for (String f : fieldNames) {
            TemplateImportField row = new TemplateImportField();
            row.importId = importId;
            row.originalName = f;
            row.mappedName = f;
            row.source = TemplateFieldSource.TEXT;
            row.confidence = 0.95d;
            row.required = false;
            row.persist();
        }

        if (fieldNames.isEmpty()) {
            warnings.add("Aucun champ détecté automatiquement. Vérifiez le PDF ou passez en mapping manuel.");
        }

        imp.status = TemplateImportStatus.FIELDS_DETECTED;
        imp.updatedAt = LocalDateTime.now();
        imp.persist();

        TemplateImportDetectResponse resp = new TemplateImportDetectResponse();
        resp.importId = importId;
        resp.status = imp.status.name();
        resp.warnings = warnings;
        resp.detectedFields = toFieldDTOs(importId);
        return resp;
    }

    public TemplateImportResponse get(UUID importId) {
        TemplateImport imp = requireImport(importId);
        TemplateImportResponse resp = toResponse(imp);
        return resp;
    }

    public List<TemplateImportResponse> list(int page, int size) {
        List<TemplateImport> items = TemplateImport.findAll()
                .page(page, size)
                .list();
        return items.stream().map(this::toResponse).toList();
    }

    @Transactional
    public TemplateImportDetectResponse updateFieldMappings(UUID importId, TemplateImportFieldsUpdateRequest req) {
        requireImport(importId);
        if (req == null || req.fields == null || req.fields.isEmpty()) {
            throw new IllegalStateException("Aucune mise à jour de mapping fournie");
        }

        Map<String, TemplateImportField> byOriginal = new LinkedHashMap<>();
        for (TemplateImportField f : TemplateImportField.<TemplateImportField>list("importId", importId)) {
            byOriginal.put(f.originalName, f);
        }

        for (TemplateImportFieldsUpdateRequest.FieldMappingItem it : req.fields) {
            if (it == null || it.original == null || it.original.isBlank()) continue;
            TemplateImportField row = byOriginal.get(it.original);
            if (row == null) continue;
            if (it.mapped != null && !it.mapped.isBlank()) row.mappedName = it.mapped.trim();
            if (it.required != null) row.required = it.required;
            row.source = TemplateFieldSource.MANUAL;
            row.persist();
        }

        TemplateImport imp = requireImport(importId);
        imp.status = TemplateImportStatus.FIELDS_CONFIRMED;
        imp.updatedAt = LocalDateTime.now();
        imp.persist();

        TemplateImportDetectResponse resp = new TemplateImportDetectResponse();
        resp.importId = importId;
        resp.status = imp.status.name();
        resp.detectedFields = toFieldDTOs(importId);
        return resp;
    }

    @Transactional
    public TemplateImportResponse publish(UUID importId, TemplateImportPublishRequest req) {
        TemplateImport imp = requireImport(importId);
        if (req == null) {
            throw new IllegalStateException("Données de publication manquantes");
        }
        if (DocumentTemplate.findByCode(req.code) != null) {
            throw new IllegalStateException("Un template avec le code '" + req.code + "' existe déjà");
        }

        List<TemplateImportField> fields = TemplateImportField.list("importId", importId);
        String html = buildHtmlTemplate(req, fields);
        String champsJson = buildChampsJson(fields);

        DocumentTemplate t = new DocumentTemplate();
        t.code = req.code;
        t.libelle = req.libelle;
        t.typeDocument = req.typeDocument;
        t.description = req.description != null ? req.description : "Template publié depuis import PDF";
        t.contenuQute = html;
        t.champsJson = champsJson;
        t.persist();

        imp.status = TemplateImportStatus.PUBLISHED;
        imp.publishedTemplateCode = req.code;
        imp.updatedAt = LocalDateTime.now();
        imp.persist();
        return toResponse(imp);
    }

    private TemplateImport requireImport(UUID importId) {
        TemplateImport imp = TemplateImport.findById(importId);
        if (imp == null) throw new NotFoundException("Import introuvable : " + importId);
        return imp;
    }

    private TemplateImportResponse toResponse(TemplateImport imp) {
        TemplateImportResponse r = new TemplateImportResponse();
        r.importId = imp.id;
        r.nom = imp.nom;
        r.fileName = imp.fileName;
        r.status = imp.status != null ? imp.status.name() : null;
        r.detectMode = imp.detectMode;
        r.pages = imp.pages;
        r.errorMessage = imp.errorMessage;
        r.publishedTemplateCode = imp.publishedTemplateCode;
        r.createdAt = imp.createdAt;
        r.updatedAt = imp.updatedAt;
        return r;
    }

    private List<TemplateImportFieldDTO> toFieldDTOs(UUID importId) {
        List<TemplateImportFieldDTO> out = new ArrayList<>();
        List<TemplateImportField> fields = TemplateImportField.list("importId", importId);
        for (TemplateImportField f : fields) {
            TemplateImportFieldDTO dto = new TemplateImportFieldDTO();
            dto.id = f.id;
            dto.name = f.originalName;
            dto.mapped = f.mappedName;
            dto.required = f.required;
            dto.source = f.source != null ? f.source.name() : null;
            dto.confidence = f.confidence;
            out.add(dto);
        }
        return out;
    }

    private byte[] readStoredPdf(String storagePath) {
        try {
            return Files.readAllBytes(Path.of(storagePath));
        } catch (IOException e) {
            throw new RuntimeException("Impossible de lire le PDF importé", e);
        }
    }

    private int readPageCount(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return doc.getNumberOfPages();
        } catch (Exception e) {
            return 1;
        }
    }

    private List<String> detectFromText(byte[] bytes, List<String> customPatterns) {
        String text = "";
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            text = stripper.getText(doc);
        } catch (Exception ignored) {
            // best effort
        }

        List<String> patterns = new ArrayList<>();
        if (customPatterns != null && !customPatterns.isEmpty()) {
            patterns.addAll(customPatterns);
        } else {
            patterns.add("\\{\\{([a-zA-Z0-9_]+)\\}\\}");
            patterns.add("\\{([a-zA-Z0-9_]+)\\}");
        }

        Set<String> fields = new LinkedHashSet<>();
        for (String p : patterns) {
            Matcher m = Pattern.compile(p).matcher(text);
            while (m.find()) {
                String field = m.group(1);
                if (field != null && !field.isBlank()) {
                    fields.add(field.trim());
                }
            }
        }
        return new ArrayList<>(fields);
    }

    private List<String> detectFromAcroForm(byte[] bytes) {
        Set<String> out = new LinkedHashSet<>();
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDAcroForm acroForm = doc.getDocumentCatalog().getAcroForm();
            if (acroForm != null) {
                for (PDField field : acroForm.getFieldTree()) {
                    String name = field.getFullyQualifiedName();
                    if (name != null && !name.isBlank()) {
                        out.add(name.trim());
                    }
                }
            }
        } catch (Exception ignored) {
            // best effort
        }
        return new ArrayList<>(out);
    }

    private String buildChampsJson(List<TemplateImportField> fields) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TemplateImportField f : fields) {
            Map<String, Object> row = new LinkedHashMap<>();
            String key = (f.mappedName != null && !f.mappedName.isBlank()) ? f.mappedName : f.originalName;
            row.put("key", key);
            row.put("required", f.required);
            row.put("source", f.source != null ? f.source.name() : TemplateFieldSource.TEXT.name());
            out.add(row);
        }
        try {
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String buildHtmlTemplate(TemplateImportPublishRequest req, List<TemplateImportField> fields) {
        String title = req.libelle != null ? req.libelle.toUpperCase(Locale.ROOT) : "DOCUMENT";
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family: Arial, sans-serif; padding: 32px; color:#1f2937;'>");
        sb.append("<h1 style='text-align:center; margin-bottom: 24px;'>").append(title).append("</h1>");
        sb.append("<p style='font-size:13px;'>Reference: {reference}</p>");
        sb.append("<p style='font-size:13px;'>Date d emission: {date_emission}</p>");
        sb.append("<hr style='margin: 14px 0 20px 0;'/>");
        sb.append("<table style='width:100%; border-collapse: collapse;'>");
        for (TemplateImportField f : fields) {
            String key = (f.mappedName != null && !f.mappedName.isBlank()) ? f.mappedName : f.originalName;
            sb.append("<tr>");
            sb.append("<td style='width:35%; padding:6px; border:1px solid #e5e7eb;'><strong>")
                    .append(escapeHtmlLabel(key))
                    .append("</strong></td>");
            sb.append("<td style='padding:6px; border:1px solid #e5e7eb;'>{")
                    .append(key)
                    .append("}</td>");
            sb.append("</tr>");
        }
        sb.append("</table>");

        if (req.includeDataMatrix == null || req.includeDataMatrix) {
            sb.append("<div style='margin-top:24px;'>");
            sb.append("<p style='font-size:12px; color:#475569;'>Verification:</p>");
            sb.append("{datamatrix_placeholder}");
            sb.append("</div>");
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private String escapeHtmlLabel(String input) {
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String normalizeFilename(String input) {
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
