package com.cev.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "template_import_field")
public class TemplateImportField extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "import_id", nullable = false, columnDefinition = "CHAR(36)")
    public UUID importId;

    @Column(name = "original_name", nullable = false, length = 120)
    public String originalName;

    @Column(name = "mapped_name", length = 120)
    public String mappedName;

    @Column(name = "required_flag", nullable = false)
    public boolean required = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    public TemplateFieldSource source = TemplateFieldSource.TEXT;

    @Column(name = "confidence")
    public Double confidence;
}
