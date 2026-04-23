package com.cev.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "template_import")
public class TemplateImport extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(columnDefinition = "CHAR(36)")
    public UUID id;

    @Column(name = "nom", length = 200)
    public String nom;

    @Column(name = "file_name", nullable = false, length = 300)
    public String fileName;

    @Column(name = "storage_path", nullable = false, length = 500)
    public String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    public TemplateImportStatus status = TemplateImportStatus.UPLOADED;

    @Enumerated(EnumType.STRING)
    @Column(name = "detect_mode", nullable = false, length = 20)
    public TemplateDetectMode detectMode = TemplateDetectMode.AUTO;

    @Column(name = "pages")
    public Integer pages;

    @Column(name = "error_message", columnDefinition = "TEXT")
    public String errorMessage;

    @Column(name = "created_by", length = 100)
    public String createdBy;

    @Column(name = "published_template_code", length = 100)
    public String publishedTemplateCode;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
