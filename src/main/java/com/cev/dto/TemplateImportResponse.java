package com.cev.dto;

import com.cev.model.TemplateDetectMode;
import com.cev.model.TemplateImportStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public class TemplateImportResponse {
    public UUID importId;
    public String nom;
    public String fileName;
    public String status;
    public TemplateDetectMode detectMode;
    public Integer pages;
    public String errorMessage;
    public String publishedTemplateCode;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public static TemplateImportResponse basic(
            UUID id,
            String nom,
            String fileName,
            TemplateImportStatus status
    ) {
        TemplateImportResponse r = new TemplateImportResponse();
        r.importId = id;
        r.nom = nom;
        r.fileName = fileName;
        r.status = status.name();
        return r;
    }
}
