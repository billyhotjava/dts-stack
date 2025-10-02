package com.yuzhi.dts.admin.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.sql.SQLException;
import org.postgresql.util.PGobject;

/**
 * Persists plain JSON strings into PostgreSQL jsonb columns without losing type information.
 */
@Converter(autoApply = false)
public class JsonbStringConverter implements AttributeConverter<String, PGobject> {

    @Override
    public PGobject convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            PGobject jsonObject = new PGobject();
            jsonObject.setType("jsonb");
            jsonObject.setValue(attribute);
            return jsonObject;
        } catch (SQLException e) {
            throw new IllegalStateException("无法序列化 JSON 字段", e);
        }
    }

    @Override
    public String convertToEntityAttribute(PGobject dbData) {
        if (dbData == null) {
            return null;
        }
        return dbData.getValue();
    }
}
