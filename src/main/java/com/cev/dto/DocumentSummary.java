package com.cev.dto;

import com.cev.model.StatutDocument;
import com.cev.model.TypeDocument;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Réponse résumée pour les listes */
public class DocumentSummary {
    public UUID id;
    public String reference;
    public String beneficiaireNom;
    public String beneficiairePrenom;
    public TypeDocument typeDocument;
    public StatutDocument statut;
    public LocalDate dateEmission;
    public LocalDateTime creeLe;
    public boolean estSigne;
}
