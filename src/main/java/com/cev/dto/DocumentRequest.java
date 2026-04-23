package com.cev.dto;

import com.cev.model.SourceDonnees;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Map;

// =====================================================
// REQUÊTES
// =====================================================

/** Requête de création / émission d'un document */
public class DocumentRequest {

    @NotBlank(message = "Le code du template est obligatoire")
    public String templateCode;

    @NotBlank(message = "Le nom du bénéficiaire est obligatoire")
    public String beneficiaireNom;

    public String beneficiairePrenom;

    @Email(message = "Email invalide")
    public String beneficiaireEmail;

    @NotNull(message = "La source des données est obligatoire")
    public SourceDonnees sourceDonnees;

    /** Données pour remplir le template (clé = champ, valeur = contenu) */
    @NotNull
    public Map<String, Object> donnees;

    /** Si source = API_EXTERNE : identifiant dans le système externe */
    public String referenceExterne;

    /** Date d'expiration du document (optionnel) */
    public LocalDate dateExpiration;

    /** Envoyer automatiquement par email après signature */
    public boolean envoyerEmail = false;
}
