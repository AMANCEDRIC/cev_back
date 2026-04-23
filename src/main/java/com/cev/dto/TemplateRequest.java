package com.cev.dto;

import com.cev.model.TypeDocument;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Requête de création d'un template */
public class TemplateRequest {

    @NotBlank
    public String code;

    @NotBlank
    public String libelle;

    @NotNull
    public TypeDocument typeDocument;

    public String description;

    @NotBlank(message = "Le contenu Qute du template est obligatoire")
    public String contenuQute;

    /** JSON : liste de définitions de champs */
    public String champsJson;
}
