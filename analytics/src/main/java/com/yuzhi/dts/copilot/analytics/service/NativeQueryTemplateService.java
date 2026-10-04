package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class NativeQueryTemplateService {

    private static final Pattern OPTIONAL_BLOCK = Pattern.compile("\\[\\[([\\s\\S]*?)\\]\\]");
    private static final Pattern TEMPLATE_TAG =
            Pattern.compile("\\{\\{\\s*([A-Za-z0-9_\\-]+)\\s*\\}\\}|\\$\\{\\s*([A-Za-z0-9_\\-]+)\\s*\\}");
    private static final Pattern PARAM_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_\\-]{0,63}$");
    private static final int MAX_PARAMETER_COUNT = 100;

    public RenderedQuery render(String sqlTemplate, JsonNode parametersNode) {
        if (sqlTemplate == null || sqlTemplate.isBlank()) {
            return new RenderedQuery("", List.of());
        }
        validateParameterWhitelist(sqlTemplate, parametersNode);
        Map<String, Object> values = parseParameters(parametersNode);
        String withoutOptionalBlocks = applyOptionalBlocks(sqlTemplate, values);
        return applyTemplateTags(withoutOptionalBlocks, values);
    }

    public void validateParameterWhitelist(String sqlTemplate, JsonNode parametersNode) {
        Set<String> templateTags = extractTags(sqlTemplate == null ? "" : sqlTemplate);
        Set<String> provided = collectProvidedParameterNames(parametersNode);

        if (provided.size() > MAX_PARAMETER_COUNT) {
            throw new IllegalArgumentException("Too many parameters, max allowed is " + MAX_PARAMETER_COUNT);
        }

        for (String name : provided) {
            if (name == null || !PARAM_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Invalid parameter name: " + name);
            }
        }

        if (templateTags.isEmpty()) {
            if (!provided.isEmpty()) {
                throw new IllegalArgumentException("This query does not accept parameters");
            }
            return;
        }

        List<String> unsupported = new ArrayList<>();
        for (String name : provided) {
            if (!templateTags.contains(name)) {
                unsupported.add(name);
            }
        }

        if (!unsupported.isEmpty()) {
            Collections.sort(unsupported);
            throw new IllegalArgumentException("Unsupported parameter(s): " + String.join(", ", unsupported));
        }
    }

    private static Set<String> collectProvidedParameterNames(JsonNode node) {
        Set<String> out = new LinkedHashSet<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return out;
        }
        if (node.isArray()) {
            for (JsonNode param : node) {
                String name = resolveParamName(param);
                if (name != null) {
                    out.add(name);
                }
            }
            return out;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(e -> out.add(e.getKey()));
            return out;
        }
        return out;
    }

    private static String applyOptionalBlocks(String template, Map<String, Object> values) {
        Matcher m = OPTIONAL_BLOCK.matcher(template);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String block = m.group(1);
            Set<String> required = extractTags(block);
            boolean include = required.isEmpty() || required.stream().allMatch(name -> hasValue(values.get(name)));
            m.appendReplacement(out, Matcher.quoteReplacement(include ? block : ""));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static RenderedQuery applyTemplateTags(String template, Map<String, Object> values) {
        Matcher m = TEMPLATE_TAG.matcher(template);
        StringBuffer out = new StringBuffer();
        List<Object> bindings = new ArrayList<>();

        while (m.find()) {
            String name = resolveTemplateTagName(m);
            Object value = values.get(name);
            if (!hasValue(value)) {
                throw new IllegalArgumentException("Missing required parameter: " + name);
            }

            if (value instanceof List<?> list) {
                if (list.isEmpty()) {
                    throw new IllegalArgumentException("Missing required parameter: " + name);
                }
                String placeholders = String.join(", ", java.util.Collections.nCopies(list.size(), "?"));
                bindings.addAll(list);
                m.appendReplacement(out, Matcher.quoteReplacement(placeholders));
            } else {
                bindings.add(value);
                m.appendReplacement(out, "?");
            }
        }
        m.appendTail(out);
        return new RenderedQuery(out.toString(), bindings);
    }

    private static Set<String> extractTags(String text) {
        Set<String> tags = new HashSet<>();
        Matcher m = TEMPLATE_TAG.matcher(text);
        while (m.find()) {
            tags.add(resolveTemplateTagName(m));
        }
        return tags;
    }

    private static String resolveTemplateTagName(Matcher matcher) {
        String name = trimToNull(matcher.group(1));
        if (name != null) {
            return name;
        }
        String fallback = trimToNull(matcher.group(2));
        return fallback == null ? "" : fallback;
    }

    private static boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String s) {
            return !s.isBlank();
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        return true;
    }

    private static Map<String, Object> parseParameters(JsonNode node) {
        Map<String, Object> out = new HashMap<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return out;
        }
        if (node.isArray()) {
            for (JsonNode param : node) {
                String name = resolveParamName(param);
                if (name == null) {
                    continue;
                }
                Object value = resolveParamValue(param.get("value"));
                out.put(name, value);
            }
            return out;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(e -> out.put(e.getKey(), resolveParamValue(e.getValue())));
            return out;
        }
        return out;
    }

    private static String resolveParamName(JsonNode param) {
        if (param == null || !param.isObject()) {
            return null;
        }
        JsonNode target = param.get("target");
        if (target != null && target.isArray() && target.size() >= 2) {
            String kind = target.get(0).asText("");
            JsonNode spec = target.get(1);
            if ("variable".equalsIgnoreCase(kind) && spec != null && spec.isArray() && spec.size() >= 2) {
                if ("template-tag".equalsIgnoreCase(spec.get(0).asText(""))) {
                    return trimToNull(spec.get(1).asText(null));
                }
            }
        }

        String name = trimToNull(param.path("name").asText(null));
        if (name != null) {
            return name;
        }
        return trimToNull(param.path("slug").asText(null));
    }

    private static Object resolveParamValue(JsonNode valueNode) {
        if (valueNode == null || valueNode.isNull() || valueNode.isMissingNode()) {
            return null;
        }
        if (valueNode.isTextual()) {
            return valueNode.asText();
        }
        if (valueNode.isNumber()) {
            return valueNode.numberValue();
        }
        if (valueNode.isBoolean()) {
            return valueNode.asBoolean();
        }
        if (valueNode.isArray()) {
            List<Object> out = new ArrayList<>();
            for (JsonNode item : valueNode) {
                Object v = resolveParamValue(item);
                if (v != null) {
                    out.add(v);
                }
            }
            return out;
        }
        if (valueNode.isObject()) {
            if (valueNode.has("value")) {
                return resolveParamValue(valueNode.get("value"));
            }
            return valueNode.toString();
        }
        return valueNode.asText();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    public record RenderedQuery(String sql, List<Object> bindings) {}
}
