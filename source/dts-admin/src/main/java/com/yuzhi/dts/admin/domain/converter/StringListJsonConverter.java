package com.yuzhi.dts.admin.domain.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.sql.SQLException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.postgresql.util.PGobject;

/**
 * Simple JSON converter for persisting {@link java.util.List} of strings into jsonb columns.
 */
@Converter(autoApply = false)
public class StringListJsonConverter implements AttributeConverter<List<String>, PGobject> {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Override
    public PGobject convertToDatabaseColumn(List<String> attribute) {
        List<String> safe = attribute == null ? Collections.emptyList() : attribute;
        try {
            PGobject jsonObject = new PGobject();
            jsonObject.setType("jsonb");
            jsonObject.setValue(MAPPER.writeValueAsString(safe));
            return jsonObject;
        } catch (IOException | SQLException e) {
            throw new IllegalArgumentException("Failed to serialize string list to JSON", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(PGobject dbData) {
        if (dbData == null) {
            return new ArrayList<>();
        }
        String value = dbData.getValue();
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(value, MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to deserialize JSON to string list", e);
        }
    }
}
