package com.yuzhi.dts.ingestion.service.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Shared fail-closed rules for managed ingestion configuration secrets. */
public final class IngestionSensitiveConfigSupport {

    private static final Set<String> SECRET_KEYS = Set.of(
        "password", "passwd", "pwd", "secret", "secrets", "secretkey", "credential", "credentials",
        "token", "accesstoken", "refreshtoken", "idtoken", "authtoken", "bearertoken", "apitoken",
        "apikey", "authorization", "proxyauthorization", "clientsecret", "privatekey", "sessionkey",
        "cookie", "setcookie"
    );
    private static final Set<String> SAFE_REFERENCE_KEYS = Set.of(
        "passwordref", "secretref", "clientsecretref", "clientsecretreference", "apikeyref", "tokenref",
        "credentialref", "keysecretref", "casecretref", "capemsecretref", "cacertificatesecretref",
        "certsecretref", "customcasecretref", "valueref", "secretversion"
    );
    private static final Set<String> NON_SECRET_CONTROL_KEYS = Set.of(
        "tokenurl", "tokenpath", "tokenplacement", "tokenheadername", "tokenparam", "nexttokenpath", "checkpointtoken"
    );
    private static final List<String> COMPOSITE_SECRET_FRAGMENTS = List.of(
        "password", "passwd", "pwd", "clientsecret", "secretkey", "privatekey", "apikey", "credential",
        "accesstoken", "refreshtoken", "idtoken", "authtoken", "bearertoken", "sessionkey",
        "authorization", "proxyauthorization", "setcookie", "cookie", "secret", "token"
    );
    private static final Set<String> HEADER_CONTAINER_KEYS = Set.of(
        "headers", "header", "httpheaders", "requestheaders", "defaultheaders"
    );
    private static final List<String> HEADER_SECRET_FRAGMENTS = List.of(
        "authorization", "apikey", "credential", "secret", "password", "accesstoken", "refreshtoken",
        "idtoken", "authtoken", "bearertoken", "sessionkey", "cookie"
    );
    private static final Set<String> HEADER_NAME_KEYS = Set.of("name", "key", "headername");
    private static final Pattern SAFE_REFERENCE_VALUE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/@-]{0,511}");
    private static final Pattern URL_USER_INFO = Pattern.compile("(?i)(//)[^/@\\s:]+:[^/@\\s]+@");
    private static final Pattern SENSITIVE_VALUE = Pattern.compile(
        "(?i)([?;&](?:password|passwd|pwd|secret|secret[_-]?key|credential|token|access_token|refresh_token|id_token|auth_token|api[_-]?key)=)[^&#;\\s]+"
    );
    private static final Pattern AUTH_VALUE = Pattern.compile("(?i)^\\s*(?:bearer|basic)\\s+.+$");
    private static final Pattern JSON_SECRET_VALUE = Pattern.compile(
        "(?i)([\"'](?:password|passwd|pwd|secret|secret[_-]?key|credential|token|access[_-]?token|refresh[_-]?token|id[_-]?token|auth[_-]?token|api[_-]?key|client[_-]?secret|authorization)[\"']\\s*:\\s*[\"'])([^\"'\\r\\n]*)([\"'])"
    );
    private static final Pattern LINE_SECRET_VALUE = Pattern.compile(
        "(?im)^(\\s*(?:password|passwd|pwd|secret|secret[_-]?key|credential|token|access[_-]?token|refresh[_-]?token|id[_-]?token|auth[_-]?token|api[_-]?key|client[_-]?secret|authorization)\\s*[:=]\\s*)([^\\r\\n]+)$"
    );
    private static final Pattern AUTH_HEADER_VALUE = Pattern.compile(
        "(?im)^(\\s*authorization\\s*:\\s*)(?:bearer|basic)\\s+[^\\r\\n]+$"
    );
    private static final Pattern INLINE_SECRET_VALUE = Pattern.compile(
        "(?i)(\\b(?:password|passwd|pwd|secret|secret[_-]?key|credential|token|access[_-]?token|refresh[_-]?token|id[_-]?token|auth[_-]?token|api[_-]?key|client[_-]?secret|authorization)\\s*[:=]\\s*)([^\\s\\r\\n\"'}]+)"
    );

    private IngestionSensitiveConfigSupport() {}

    /**
     * Compatibility entry point retained for existing callers. Managed credentials
     * are never inherited into a task row; only the incoming public configuration
     * survives.
     */
    public static JsonNode preserveAbsentSecrets(JsonNode managed, JsonNode incoming) {
        return incoming == null ? null : stripRawSecrets(incoming);
    }

    public static void assertNoRawSecrets(JsonNode node, String fieldName) {
        if (containsRawSecrets(node)) {
            String safeField = fieldName == null || fieldName.isBlank() ? "configuration" : fieldName;
            throw new IllegalArgumentException(
                safeField + " must not contain raw credentials; use a managed data source or secret reference"
            );
        }
    }

    public static boolean containsRawSecrets(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return false;
        }
        return !node.equals(sanitize(node));
    }

    public static JsonNode stripRawSecrets(JsonNode node) {
        return sanitize(node);
    }

    public static boolean isRawSecretKeyName(String key) {
        return isRawSecretKey(normalizeKey(key));
    }

    public static JsonNode sanitize(JsonNode node) {
        if (node == null || node.isNull()) {
            return JsonNodeFactory.instance.nullNode();
        }
        JsonNode sanitized = sanitizeNode(node);
        return sanitized == null ? JsonNodeFactory.instance.nullNode() : sanitized;
    }

    private static JsonNode sanitizeNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (node.isObject()) {
            if (isSensitiveHeaderEntry(node)) {
                return null;
            }
            ObjectNode sanitized = JsonNodeFactory.instance.objectNode();
            node.fields().forEachRemaining(entry -> {
                String normalizedKey = normalizeKey(entry.getKey());
                JsonNode value = entry.getValue();
                if (isAllowedReference(normalizedKey, value)) {
                    sanitized.set(entry.getKey(), value.deepCopy());
                    return;
                }
                if (SAFE_REFERENCE_KEYS.contains(normalizedKey) || isRawSecretKey(normalizedKey)) {
                    return;
                }
                JsonNode child = HEADER_CONTAINER_KEYS.contains(normalizedKey)
                    ? sanitizeHeaderContainer(value)
                    : sanitizeNode(value);
                if (child != null) {
                    sanitized.set(entry.getKey(), child);
                }
            });
            return sanitized;
        }
        if (node.isArray()) {
            ArrayNode sanitized = JsonNodeFactory.instance.arrayNode();
            node.forEach(item -> {
                JsonNode child = sanitizeNode(item);
                if (child != null) {
                    sanitized.add(child);
                }
            });
            return sanitized;
        }
        if (node.isTextual()) {
            return TextNode.valueOf(sanitizeText(node.asText()));
        }
        return node.deepCopy();
    }

    private static JsonNode sanitizeHeaderContainer(JsonNode node) {
        if (node == null || node.isNull()) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (node.isObject()) {
            ObjectNode sanitized = JsonNodeFactory.instance.objectNode();
            node.fields().forEachRemaining(entry -> {
                if (!isSensitiveHeaderName(entry.getKey())) {
                    JsonNode child = sanitizeNode(entry.getValue());
                    if (child != null) {
                        sanitized.set(entry.getKey(), child);
                    }
                }
            });
            return sanitized;
        }
        if (node.isArray()) {
            ArrayNode sanitized = JsonNodeFactory.instance.arrayNode();
            node.forEach(item -> {
                JsonNode child = sanitizeNode(item);
                if (child != null) {
                    sanitized.add(child);
                }
            });
            return sanitized;
        }
        return sanitizeNode(node);
    }

    private static boolean isSensitiveHeaderEntry(JsonNode node) {
        if (node == null || !node.isObject()) {
            return false;
        }
        java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            java.util.Map.Entry<String, JsonNode> entry = fields.next();
            if (HEADER_NAME_KEYS.contains(normalizeKey(entry.getKey()))
                && entry.getValue().isTextual()
                && isSensitiveHeaderName(entry.getValue().asText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSensitiveHeaderName(String name) {
        String normalized = normalizeKey(name);
        if (SECRET_KEYS.contains(normalized)) {
            return true;
        }
        for (String fragment : HEADER_SECRET_FRAGMENTS) {
            if (normalized.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowedReference(String normalizedKey, JsonNode value) {
        return SAFE_REFERENCE_KEYS.contains(normalizedKey)
            && value != null
            && value.isTextual()
            && SAFE_REFERENCE_VALUE.matcher(value.asText()).matches();
    }

    /** Redacts secret-bearing external response/error text before logging. */
    public static String sanitizeText(String value) {
        if (value == null) {
            return null;
        }
        if (AUTH_VALUE.matcher(value).matches()) {
            return "[redacted]";
        }
        String sanitized = URL_USER_INFO.matcher(value).replaceAll("$1[redacted]@");
        sanitized = SENSITIVE_VALUE.matcher(sanitized).replaceAll("$1[redacted]");
        sanitized = JSON_SECRET_VALUE.matcher(sanitized).replaceAll("$1[redacted]$3");
        sanitized = AUTH_HEADER_VALUE.matcher(sanitized).replaceAll("$1[redacted]");
        sanitized = LINE_SECRET_VALUE.matcher(sanitized).replaceAll("$1[redacted]");
        return INLINE_SECRET_VALUE.matcher(sanitized).replaceAll("$1[redacted]");
    }

    private static boolean isRawSecretKey(String normalizedKey) {
        // Match only credential-bearing fields. API pagination/auth-flow controls
        // such as tokenUrl, tokenPath, tokenPlacement, tokenHeaderName,
        // nextTokenPath and checkpointToken are business configuration and must
        // survive persistence and response sanitization.
        if (SAFE_REFERENCE_KEYS.contains(normalizedKey) || NON_SECRET_CONTROL_KEYS.contains(normalizedKey)) {
            return false;
        }
        if (SECRET_KEYS.contains(normalizedKey)) {
            return true;
        }
        for (String fragment : COMPOSITE_SECRET_FRAGMENTS) {
            if (normalizedKey.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeKey(String key) {
        if (key == null) {
            return "";
        }
        String normalized = Normalizer.normalize(key.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[^a-z0-9]", "");
    }
}
