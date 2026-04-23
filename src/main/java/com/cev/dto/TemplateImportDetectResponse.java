package com.cev.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TemplateImportDetectResponse {
    public UUID importId;
    public String status;
    public List<TemplateImportFieldDTO> detectedFields = new ArrayList<>();
    public List<String> warnings = new ArrayList<>();
}
