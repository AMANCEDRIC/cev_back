package com.cev.dto;

import com.cev.model.TypeDocument;

import java.time.LocalDateTime;
import java.util.UUID;

/** Réponse template */
public class TemplateResponse {
    public UUID id;
    public String code;
    public String libelle;
    public TypeDocument typeDocument;
    public String description;
    public String champsJson;
    public boolean actif;
    public LocalDateTime creeLe;
}
