package com.cev.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "document_verification")
public class DocumentVerification extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(length = 100)
    public String reference;

    @Column(name = "hash_soumis", length = 512)
    public String hashSoumis;

    @Column(nullable = false)
    public boolean resultat;

    @Column(columnDefinition = "TEXT")
    public String detail;

    @Column(name = "verifie_le")
    public LocalDateTime verifieLe = LocalDateTime.now();

    @Column(name = "ip_source", length = 50)
    public String ipSource;
}
