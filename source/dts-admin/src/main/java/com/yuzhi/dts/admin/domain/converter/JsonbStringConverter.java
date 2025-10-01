package com.yuzhi.dts.admin.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.postgresql.util.PGobject;

/**
 * Persists plain JSON strings into PostgreSQL jsonb columns without losing type information.
 */
@Converter(autoApply = false)
public class JsonbStringConverter implements AttributeConverter<String, Object> {

    @Override
    public Object convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            PGobject jsonObject = new PGobject();
            jsonObject.setType("jsonb");
            jsonObject.setValue(attribute);
            return jsonObject;
        } catch (Exception e) {
            throw new IllegalStateException("无法序列化 JSON 字段", e);
        }
    }

    @Override
    public String convertToEntityAttribute(Object dbData) {
        if (dbData == null) {
            return null;
        }
        if (dbData instanceof PGobject pgObject) {
            return pgObject.getValue();
        }
        return dbData.toString();
    }
}
