package com.yuzhi.dts.metrics.service;

import com.yuzhi.dts.metrics.config.DtsMetricsProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class PlatformContractClient {

    private final DtsMetricsProperties properties;
    private final RestClient restClient;

    public PlatformContractClient(DtsMetricsProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public Map<String, Object> describeContract() {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("platformBaseUrl", normalizeBaseUrl());
        contract.put("apiPath", normalizeApiPath());
        contract.put("serviceName", properties.getServiceName());
        contract.put("serviceTokenConfigured", StringUtils.hasText(properties.getPlatform().getServiceToken()));
        contract.put("authHeaders", Map.of("service", "X-DTS-Service", "token", "X-DTS-Service-Token"));
        return contract;
    }

    public String internalUrl(String path) {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizeBaseUrl() + normalizeApiPath() + normalizedPath;
    }

    public RestClient.RequestHeadersSpec<?> withServiceAuth(RestClient.RequestHeadersUriSpec<?> request, String path) {
        RestClient.RequestHeadersSpec<?> spec = request
            .uri(internalUrl(path))
            .header("X-DTS-Service", properties.getServiceName());
        if (StringUtils.hasText(properties.getPlatform().getServiceToken())) {
            spec = spec.header("X-DTS-Service-Token", properties.getPlatform().getServiceToken());
        }
        return spec;
    }

    private String normalizeBaseUrl() {
        return stripTrailingSlash(properties.getPlatform().getBaseUrl());
    }

    private String normalizeApiPath() {
        String apiPath = properties.getPlatform().getApiPath();
        if (!StringUtils.hasText(apiPath) || "/".equals(apiPath)) {
            return "";
        }
        return apiPath.startsWith("/") ? stripTrailingSlash(apiPath) : "/" + stripTrailingSlash(apiPath);
    }

    private static String stripTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        while (result.endsWith("/") && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
