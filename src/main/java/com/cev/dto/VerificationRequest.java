package com.cev.dto;

import jakarta.validation.constraints.NotBlank;

/** Requête de vérification d'un document par DataMatrix */
public class VerificationRequest {

    @NotBlank(message = "La référence est obligatoire")
    public String reference;

    @NotBlank(message = "Le hash est obligatoire")
    public String hash;

    /** Payload complet du DataMatrix scanné */
    public String datamatrixPayload;
}
