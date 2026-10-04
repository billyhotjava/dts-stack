package com.yuzhi.dts.ingestion.service.etl;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

final class FileSourceColumnNames {

    private FileSourceColumnNames() {}

    static Set<String> reservedTechnicalNames() {
        return new LinkedHashSet<>(
            java.util.List.of(
                DtsOdsTechnicalColumns.SOURCE_SYSTEM,
                DtsOdsTechnicalColumns.SOURCE_TABLE,
                DtsOdsTechnicalColumns.IMPORT_TIME,
                DtsOdsTechnicalColumns.BATCH_ID,
                DtsOdsTechnicalColumns.EXECUTION_ID,
                DtsOdsTechnicalColumns.TASK_ID,
                DtsOdsTechnicalColumns.SOURCE_FILE,
                DtsOdsTechnicalColumns.SOURCE_SHEET,
                DtsOdsTechnicalColumns.FILE_HASH,
                DtsOdsTechnicalColumns.ROW_NUMBER
            )
        );
    }

    static String resolveName(JsonNode column) {
        if (column == null || column.isNull()) {
            return "";
        }
        String name = column.has("safeName") ? column.get("safeName").asText("") : "";
        if (!StringUtils.hasText(name)) {
            name = column.has("name") ? column.get("name").asText("") : "";
        }
        if (!StringUtils.hasText(name)) {
            name = column.has("label") ? column.get("label").asText("") : "";
        }
        return name;
    }

    static String resolveName(Map<String, Object> column) {
        if (column == null) {
            return "";
        }
        String name = normalizeText(column.get("safeName"));
        if (!StringUtils.hasText(name)) {
            name = normalizeText(column.get("name"));
        }
        if (!StringUtils.hasText(name)) {
            name = normalizeText(column.get("label"));
        }
        return name;
    }

    static String uniqueColumnName(String rawName, Set<String> usedNames) {
        if (!StringUtils.hasText(rawName)) {
            return "";
        }
        String base = rawName.trim().toLowerCase(Locale.ROOT);
        String candidate = base;
        int suffix = 2;
        while (!usedNames.add(candidate)) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private static String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }
}
