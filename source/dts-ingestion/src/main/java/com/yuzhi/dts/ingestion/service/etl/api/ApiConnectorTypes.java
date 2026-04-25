package com.yuzhi.dts.ingestion.service.etl.api;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;

public final class ApiConnectorTypes {

    public static final String CONNECTOR_TYPE = "api";
    public static final String DEFAULT_READER_TYPE = "httpreader";

    private static final List<String> API_SOURCE_TYPE_LIST = List.of(
        "api",
        "http",
        "https",
        "http_api",
        "api_http",
        "rest",
        "rest_api",
        "httpreader"
    );
    private static final Set<String> API_SOURCE_TYPES = Set.copyOf(API_SOURCE_TYPE_LIST);

    private ApiConnectorTypes() {}

    public static boolean isApiSourceType(String sourceType) {
        String normalized = normalize(sourceType);
        return StringUtils.hasText(normalized) && API_SOURCE_TYPES.contains(normalized);
    }

    public static String normalizeConnectorType(String connectorType) {
        String normalized = normalize(connectorType);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return isApiSourceType(normalized) ? CONNECTOR_TYPE : normalized;
    }

    public static List<String> supportedSourceTypes() {
        return API_SOURCE_TYPE_LIST;
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }
}
