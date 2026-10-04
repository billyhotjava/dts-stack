package com.yuzhi.dts.ingestion.service.etl.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

final class JsonPathLite {

    private JsonPathLite() {}

    static JsonNode read(JsonNode root, String path) {
        if (root == null || !StringUtils.hasText(path)) {
            return MissingNode.getInstance();
        }
        String normalized = normalize(path);
        if (!StringUtils.hasText(normalized)) {
            return root;
        }
        JsonNode cursor = root;
        for (String segment : normalized.split("\\.")) {
            if (!StringUtils.hasText(segment)) {
                continue;
            }
            cursor = applySegment(cursor, segment);
            if (cursor.isMissingNode()) {
                return cursor;
            }
        }
        return cursor == null ? MissingNode.getInstance() : cursor;
    }

    static List<JsonNode> readRecords(JsonNode root, String path) {
        JsonNode node = read(root, path);
        if (node.isMissingNode() || node.isNull()) {
            throw new ApiHttpException("API_RUNTIME_RESPONSE_PARSE", "recordPath 未匹配到响应字段: " + path, null, 1);
        }
        if (node.isArray()) {
            List<JsonNode> records = new ArrayList<>();
            node.forEach(records::add);
            return List.copyOf(records);
        }
        return List.of(node);
    }

    private static String normalize(String path) {
        String normalized = path.trim();
        if (normalized.startsWith("$.")) {
            return normalized.substring(2);
        }
        if (normalized.startsWith(".")) {
            return normalized.substring(1);
        }
        if ("$".equals(normalized)) {
            return "";
        }
        return normalized;
    }

    private static JsonNode applySegment(JsonNode root, String segment) {
        JsonNode cursor = root;
        String remaining = segment;
        int bracket = remaining.indexOf('[');
        if (bracket > 0) {
            cursor = cursor.path(remaining.substring(0, bracket));
            remaining = remaining.substring(bracket);
        } else if (bracket < 0) {
            return cursor.path(remaining);
        }
        while (StringUtils.hasText(remaining)) {
            if (!remaining.startsWith("[")) {
                return MissingNode.getInstance();
            }
            int end = remaining.indexOf(']');
            if (end <= 1) {
                return MissingNode.getInstance();
            }
            String indexText = remaining.substring(1, end);
            try {
                cursor = cursor.path(Integer.parseInt(indexText));
            } catch (NumberFormatException ex) {
                return MissingNode.getInstance();
            }
            remaining = remaining.substring(end + 1);
        }
        return cursor;
    }
}
