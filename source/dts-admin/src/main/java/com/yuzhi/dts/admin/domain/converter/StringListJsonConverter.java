package com.yuzhi.dts.admin.domain.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simple JSON converter for persisting {@link java.util.List} of strings into jsonb columns.
 */
@Converter(autoApply = false)
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        List<String> safe = attribute == null ? Collections.emptyList() : attribute;
        try {
            return MAPPER.writeValueAsString(safe);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize string list to JSON", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(dbData, MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to deserialize JSON to string list", e);
        }
    }
}
