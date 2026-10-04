package com.yuzhi.dts.ingestion.service.etl.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

class CursorTracker {

    String startValue(Map<String, Object> cursorPolicy) {
        return startValue(cursorPolicy, null);
    }

    String startValue(Map<String, Object> cursorPolicy, String checkpointValue) {
        String baseValue = StringUtils.hasText(checkpointValue) ? checkpointValue : firstText(cursorPolicy, "initialValue");
        if (!StringUtils.hasText(baseValue)) {
            return null;
        }
        if ("datetime".equalsIgnoreCase(firstText(cursorPolicy, "type"))) {
            Instant instant = parseInstant(baseValue);
            int lookbackSeconds = positiveInt(cursorPolicy.get("lookbackSeconds"), 0);
            return instant.minusSeconds(Math.max(0, lookbackSeconds)).toString();
        }
        return baseValue;
    }

    String advance(List<JsonNode> records, Map<String, Object> cursorPolicy) {
        if (records == null || records.isEmpty()) {
            return null;
        }
        String field = firstText(cursorPolicy, "field");
        if (!StringUtils.hasText(field)) {
            return null;
        }
        String type = firstText(cursorPolicy, "type");
        Comparator<CursorValue> comparator = comparator(type);
        CursorValue max = null;
        for (JsonNode record : records) {
            JsonNode node = JsonPathLite.read(record, field);
            if (node.isMissingNode() || node.isNull()) {
                continue;
            }
            String raw = node.isTextual() ? node.asText() : node.toString();
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            CursorValue value = toCursorValue(raw, type);
            if (max == null || comparator.compare(value, max) > 0) {
                max = value;
            }
        }
        return max == null ? null : max.raw();
    }

    private Comparator<CursorValue> comparator(String type) {
        if ("numeric".equalsIgnoreCase(type)) {
            return Comparator.comparing(value -> (BigDecimal) value.comparable());
        }
        if ("datetime".equalsIgnoreCase(type)) {
            return Comparator.comparing(value -> (Instant) value.comparable());
        }
        return Comparator.comparing(value -> (String) value.comparable());
    }

    private CursorValue toCursorValue(String raw, String type) {
        if ("numeric".equalsIgnoreCase(type)) {
            return new CursorValue(raw, new BigDecimal(raw.trim()));
        }
        if ("datetime".equalsIgnoreCase(type)) {
            return new CursorValue(raw, parseInstant(raw));
        }
        return new CursorValue(raw, raw);
    }

    private Instant parseInstant(String raw) {
        String value = raw.trim();
        try {
            long epoch = Long.parseLong(value);
            if (Math.abs(epoch) >= 10_000_000_000L) {
                return Instant.ofEpochMilli(epoch);
            }
            return Instant.ofEpochSecond(epoch);
        } catch (NumberFormatException ignored) {
            // fall through to ISO parser
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw new ApiHttpException("API_RUNTIME_CURSOR", "游标时间值无法解析: " + raw, null, 1, ex);
        }
    }

    private String firstText(Map<String, Object> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private int positiveInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue() > 0 ? number.intValue() : defaultValue;
        }
        if (value != null) {
            try {
                int parsed = Integer.parseInt(String.valueOf(value));
                return parsed > 0 ? parsed : defaultValue;
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private record CursorValue(String raw, Object comparable) {}
}
