package com.cev.dto;

import java.util.ArrayList;
import java.util.List;

public class TemplateImportFieldsUpdateRequest {
    public List<FieldMappingItem> fields = new ArrayList<>();

    public static class FieldMappingItem {
        public String original;
        public String mapped;
        public Boolean required;
    }
}
