package com.cev.dto;

import com.cev.model.TypeDocument;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class TemplateImportPublishRequest {
    @NotBlank
    public String code;

    @NotBlank
    public String libelle;

    @NotNull
    public TypeDocument typeDocument;

    public String description;
    public Boolean includeDataMatrix = true;
}
