package com.cev.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_historique")
public class DocumentHistorique extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "document_id", nullable = false, columnDefinition = "CHAR(36)")
    public UUID documentId;

    @Column(nullable = false, length = 100)
    public String action;

    @Column(columnDefinition = "TEXT")
    public String detail;

    @Column(name = "effectue_par", length = 100)
    public String effectuePar;

    @Column(name = "effectue_le")
    public LocalDateTime effectueLe = LocalDateTime.now();
}
