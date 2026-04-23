package com.cev.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "document_emis")
public class DocumentEmis extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(columnDefinition = "CHAR(36)")
    public UUID id;

    @Column(unique = true, nullable = false, length = 100)
    public String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    public DocumentTemplate template;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_document", nullable = false)
    public TypeDocument typeDocument;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_donnees", nullable = false)
    public SourceDonnees sourceDonnees;

    // -- Bénéficiaire --
    @Column(name = "beneficiaire_nom", nullable = false, length = 200)
    public String beneficiaireNom;

    @Column(name = "beneficiaire_prenom", length = 200)
    public String beneficiairePrenom;

    @Column(name = "beneficiaire_email", length = 200)
    public String beneficiaireEmail;

    @Column(name = "beneficiaire_meta", columnDefinition = "JSON")
    public String beneficiaireMeta = "{}";

    // -- Données du document --
    @Column(name = "donnees_json", columnDefinition = "JSON", nullable = false)
    public String donneesJson = "{}";

    // -- Statut --
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public StatutDocument statut = StatutDocument.BROUILLON;

    // -- Signature CEV --
    @Column(name = "hash_signature", length = 512)
    public String hashSignature;

    @Column(name = "datamatrix_payload", columnDefinition = "TEXT")
    public String datamatrixPayload;

    @Column(name = "signe_le")
    public LocalDateTime signeLe;

    @Column(name = "signe_par", length = 100)
    public String signePar = "CEV-AUTO";

    // -- Stockage --
    @Column(name = "pdf_path", length = 500)
    public String pdfPath;

    @Column(name = "pdf_nom_fichier", length = 200)
    public String pdfNomFichier;

    // -- Dates --
    @Column(name = "date_emission", nullable = false)
    public LocalDate dateEmission = LocalDate.now();

    @Column(name = "date_expiration")
    public LocalDate dateExpiration;

    @Column(name = "cree_le")
    public LocalDateTime creeLe = LocalDateTime.now();

    @Column(name = "modifie_le")
    public LocalDateTime modifieLe = LocalDateTime.now();

    @Column(name = "cree_par", length = 100)
    public String creePar;

    // -----------------------------------------------
    // Méthodes Panache
    // -----------------------------------------------
    public static DocumentEmis findByReference(String reference) {
        return find("reference", reference).firstResult();
    }

    public static List<DocumentEmis> findByStatut(StatutDocument statut) {
        return list("statut", statut);
    }

    public static List<DocumentEmis> findByEmail(String email) {
        return list("beneficiaireEmail", email);
    }

    public static long countByStatut(StatutDocument statut) {
        return count("statut", statut);
    }

    @PreUpdate
    public void preUpdate() {
        this.modifieLe = LocalDateTime.now();
    }
}
