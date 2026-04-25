package com.yuzhi.dts.platform.service.infra;

import com.yuzhi.dts.platform.service.infra.dto.DataSourceRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

final class ApiDataSourceSupport {

    static final String CONNECTOR_TYPE = "api";
    static final String DEFAULT_READER_TYPE = "httpreader";

    private static final Set<String> API_TYPES = Set.of("api", "http", "https", "http_api", "api_http", "rest", "rest_api", "httpreader");
    private static final Set<String> SENSITIVE_KEYS = Set.of(
        "authorization",
        "apikey",
        "api_key",
        "token",
        "accesstoken",
        "access_token",
        "refreshtoken",
        "refresh_token",
        "bearertoken",
        "bearer_token",
        "password",
        "clientsecret",
        "client_secret",
        "privatekey",
        "private_key",
        "secret"
    );

    private ApiDataSourceSupport() {}

    static boolean isApiType(String type) {
        String normalized = normalize(type);
        return StringUtils.hasText(normalized) && API_TYPES.contains(normalized);
    }

    static void validateRequest(DataSourceRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        if (StringUtils.hasText(request.jdbcUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API 数据源不允许填写 JDBC 地址");
        }
        Map<String, Object> props = request.props();
        String baseUrl = extractBaseUrl(props);
        if (!StringUtils.hasText(baseUrl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API 数据源 baseUrl 不能为空");
        }
        validateBaseUrl(baseUrl);
        String leakedKey = findSensitivePlaintextKey(props);
        if (StringUtils.hasText(leakedKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "敏感字段 " + leakedKey + " 必须保存到 secrets，不能写入 props");
        }
    }

    static Map<String, Object> normalizeProps(Map<String, Object> props) {
        Map<String, Object> normalized = props == null ? new LinkedHashMap<>() : new LinkedHashMap<>(props);
        normalized.put("connectorType", CONNECTOR_TYPE);
        normalized.putIfAbsent("readerType", DEFAULT_READER_TYPE);
        normalized.putIfAbsent("sourceCategory", CONNECTOR_TYPE);
        normalized.putIfAbsent("contractVersion", "1.0.0");
        if (!StringUtils.hasText(extractAuthProvider(normalized))) {
            normalized.put("authProvider", "none");
        }
        return normalized;
    }

    private static String extractBaseUrl(Map<String, Object> props) {
        if (props == null || props.isEmpty()) {
            return null;
        }
        Object direct = props.get("baseUrl");
        if (direct == null) {
            direct = props.get("baseURL");
        }
        if (StringUtils.hasText(asText(direct))) {
            return asText(direct);
        }
        Object api = props.get("api");
        if (api instanceof Map<?, ?> map) {
            Object nested = map.get("baseUrl");
            if (nested == null) {
                nested = map.get("baseURL");
            }
            return asText(nested);
        }
        return null;
    }

    private static String extractAuthProvider(Map<String, Object> props) {
        Object direct = props.get("authProvider");
        if (StringUtils.hasText(asText(direct))) {
            return asText(direct);
        }
        Object auth = props.get("auth");
        if (auth instanceof Map<?, ?> map) {
            String provider = asText(map.get("provider"));
            if (StringUtils.hasText(provider)) {
                return provider;
            }
        }
        Object api = props.get("api");
        if (api instanceof Map<?, ?> apiMap) {
            Object nestedDirect = apiMap.get("authProvider");
            if (StringUtils.hasText(asText(nestedDirect))) {
                return asText(nestedDirect);
            }
            Object nestedAuth = apiMap.get("auth");
            if (nestedAuth instanceof Map<?, ?> authMap) {
                return asText(authMap.get("provider"));
            }
        }
        return null;
    }

    private static void validateBaseUrl(String baseUrl) {
        try {
            URI uri = new URI(baseUrl.trim());
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API 数据源 baseUrl 仅支持 http/https");
            }
            if (!StringUtils.hasText(uri.getHost())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API 数据源 baseUrl host 不能为空");
            }
        } catch (URISyntaxException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API 数据源 baseUrl 格式不合法");
        }
    }

    private static String findSensitivePlaintextKey(Object value) {
        return findSensitivePlaintextKey(value, "");
    }

    private static String findSensitivePlaintextKey(Object value, String path) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() == null ? "" : entry.getKey().toString();
                String currentPath = StringUtils.hasText(path) ? path + "." + key : key;
                if (isSensitivePlaintextKey(currentPath, key) && StringUtils.hasText(asText(entry.getValue()))) {
                    return currentPath;
                }
                String nested = findSensitivePlaintextKey(entry.getValue(), currentPath);
                if (StringUtils.hasText(nested)) {
                    return nested;
                }
            }
        }
        if (value instanceof Iterable<?> iterable) {
            int index = 0;
            for (Object item : iterable) {
                String nested = findSensitivePlaintextKey(item, path + "[" + index + "]");
                if (StringUtils.hasText(nested)) {
                    return nested;
                }
                index++;
            }
        }
        return null;
    }

    private static boolean isSensitivePlaintextKey(String path, String key) {
        String normalized = normalizeKey(key);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        if (normalized.endsWith("secretref") || normalized.endsWith("secretversion") || normalized.endsWith("maskeddisplay")) {
            return false;
        }
        String normalizedPath = normalizeKey(path);
        if (normalizedPath != null && normalizedPath.contains("secretrefs")) {
            return false;
        }
        if ("value".equals(normalized) && pathHasSegment(normalizedPath, "auth")) {
            return true;
        }
        return SENSITIVE_KEYS.contains(normalized);
    }

    private static boolean pathHasSegment(String normalizedPath, String segment) {
        if (!StringUtils.hasText(normalizedPath) || !StringUtils.hasText(segment)) {
            return false;
        }
        for (String part : normalizedPath.split("[.\\[\\]]+")) {
            if (segment.equals(part)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    private static String normalizeKey(String key) {
        return StringUtils.hasText(key) ? key.trim().replace("-", "").toLowerCase(Locale.ROOT) : null;
    }

    private static String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return StringUtils.hasText(text) ? text : null;
    }
}
