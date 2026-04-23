package com.cev.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "document_template")
public class DocumentTemplate extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(columnDefinition = "CHAR(36)")
    public UUID id;

    @Column(unique = true, nullable = false, length = 50)
    public String code;

    @Column(nullable = false, length = 200)
    public String libelle;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_document", nullable = false)
    public TypeDocument typeDocument;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Column(name = "contenu_qute", columnDefinition = "TEXT", nullable = false)
    public String contenuQute;

    @Column(name = "champs_json", columnDefinition = "JSON")
    public String champsJson;

    @Column(nullable = false)
    public boolean actif = true;

    @Column(name = "cree_le")
    public LocalDateTime creeLe = LocalDateTime.now();

    @Column(name = "modifie_le")
    public LocalDateTime modifieLe = LocalDateTime.now();

    // -----------------------------------------------
    // Méthodes de requête Panache
    // -----------------------------------------------
    public static List<DocumentTemplate> findActifs() {
        return list("actif", true);
    }

    public static DocumentTemplate findByCode(String code) {
        return find("code", code).firstResult();
    }

    @PreUpdate
    public void preUpdate() {
        this.modifieLe = LocalDateTime.now();
    }
}
