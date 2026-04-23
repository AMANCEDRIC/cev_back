package com.cev.dto;

import com.cev.model.StatutDocument;
import com.cev.model.TypeDocument;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Réponse de vérification d'un document */
public class VerificationResponse {
    public boolean valide;
    public String reference;
    public String message;
    public String beneficiaireNom;
    public String beneficiairePrenom;
    public TypeDocument typeDocument;
    public LocalDate dateEmission;
    public LocalDate dateExpiration;
    public String signePar;
    public LocalDateTime signeLe;
    public StatutDocument statut;
}
