package com.cev.dto;

import com.cev.model.SourceDonnees;
import com.cev.model.StatutDocument;
import com.cev.model.TypeDocument;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** Réponse complète d'un document émis */
public class DocumentResponse {
    public UUID id;
    public String reference;
    public String templateCode;
    public TypeDocument typeDocument;
    public SourceDonnees sourceDonnees;
    public String beneficiaireNom;
    public String beneficiairePrenom;
    public String beneficiaireEmail;
    public StatutDocument statut;
    public String hashSignature;
    public String datamatrixPayload;
    public LocalDateTime signeLe;
    public String signePar;
    public String pdfUrl;
    public LocalDate dateEmission;
    public LocalDate dateExpiration;
    public LocalDateTime creeLe;
    public String creePar;
    public Map<String, Object> donnees;
}
