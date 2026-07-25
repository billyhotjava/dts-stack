package com.yuzhi.dts.metrics.service;

import com.yuzhi.dts.metrics.service.PlatformContractClient.TaggableAssetRegistration;
import com.yuzhi.dts.metrics.service.PlatformContractClient.TaggableAssetsRegisterRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MetricTaggableAssetRegistrar {

    private static final int MAX_REGISTRATIONS = 500;

    private final PlatformContractClient platformContractClient;
    private final boolean enabled;

    public MetricTaggableAssetRegistrar(
        PlatformContractClient platformContractClient,
        @Value(
            "${dts.metrics.platform.taggable-asset-register-enabled:false}"
        ) boolean enabled
    ) {
        this.platformContractClient = platformContractClient;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Map<String, Object> register(
        String modelId,
        Map<String, Object> publishResult,
        Map<String, Object> graph
    ) {
        if (!enabled) {
            return Map.of();
        }
        String version = required(
            publishResult == null ? null : publishResult.get("version"),
            "published metric version is required"
        );
        String packId = firstText(
            graph == null ? null : graph.get("packId"),
            graph == null ? null : graph.get("pack_id"),
            modelId
        );
        String tenantNamespace = firstText(
            graph == null ? null : graph.get("tenantNamespace"),
            graph == null ? null : graph.get("tenant_namespace"),
            graph == null ? null : graph.get("tenant"),
            "default"
        );
        String ownerDept = firstText(
            graph == null ? null : graph.get("ownerDept"),
            graph == null ? null : graph.get("owner_dept")
        );
        String registrationScope = metricScope(
            tenantNamespace,
            packId
        );
        UUID syncRunId = UUID.randomUUID();
        List<TaggableAssetRegistration> assets = new ArrayList<>();
        String metricPackKey = metricPackKey(
            tenantNamespace,
            packId,
            version
        );
        assets.add(
            new TaggableAssetRegistration(
                "METRIC_PACK",
                metricPackKey,
                boundedRemoteId("metric-pack", metricPackKey),
                ownerDept
            )
        );
        for (String code : metricCodes(graph)) {
            String metricKey = metricKey(
                tenantNamespace,
                packId,
                code
            );
            assets.add(
                new TaggableAssetRegistration(
                    "METRIC",
                    metricKey,
                    boundedRemoteId("metric", metricKey),
                    ownerDept
                )
            );
        }
        int registered = 0;
        for (int start = 0; start < assets.size(); start += MAX_REGISTRATIONS) {
            int end = Math.min(start + MAX_REGISTRATIONS, assets.size());
            Map<String, Object> response =
                platformContractClient.registerTaggableAssets(
                    new TaggableAssetsRegisterRequest(
                        List.copyOf(assets.subList(start, end)),
                        registrationScope,
                        syncRunId,
                        end == assets.size()
                    )
                );
            registered += registrationCount(response);
        }
        return Map.of("registered", registered);
    }

    private int registrationCount(Map<String, Object> response) {
        Object value = response == null ? null : response.get("registered");
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException(
            "taggable asset registration count is required"
        );
    }

    private LinkedHashSet<String> metricCodes(Map<String, Object> graph) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        if (graph == null || graph.isEmpty()) {
            return codes;
        }
        addCodes(codes, graph.get("measures"));
        Object derived = graph.get("derived_metrics");
        if (derived instanceof List<?> values) {
            for (Object value : values) {
                if (value instanceof Map<?, ?> item) {
                    String code = firstText(
                        item.get("id"),
                        item.get("code"),
                        item.get("name")
                    );
                    addCode(codes, code);
                } else if (value != null) {
                    addCode(codes, String.valueOf(value));
                }
            }
        }
        return codes;
    }

    private void addCodes(LinkedHashSet<String> codes, Object raw) {
        if (!(raw instanceof List<?> values)) {
            return;
        }
        for (Object value : values) {
            String code;
            if (value instanceof Map<?, ?> item) {
                code = firstText(
                    item.get("id"),
                    item.get("code"),
                    item.get("name")
                );
            } else {
                code = value == null ? null : String.valueOf(value);
            }
            addCode(codes, code);
        }
    }

    private void addCode(LinkedHashSet<String> codes, String code) {
        if (StringUtils.hasText(code)) {
            codes.add(segment(code));
        }
    }

    private String metricKey(
        String tenantNamespace,
        String packId,
        String metricCode
    ) {
        return (
            metricScope(tenantNamespace, packId) +
            "/metric:" +
            segment(required(metricCode, "metric code is required"))
        );
    }

    private String metricPackKey(
        String tenantNamespace,
        String packId,
        String version
    ) {
        return (
            metricScope(tenantNamespace, packId) +
            "/version:" +
            segment(version)
        );
    }

    private String metricScope(
        String tenantNamespace,
        String packId
    ) {
        return (
            "tenant:" +
            segment(tenantNamespace) +
            "/env:prod/dialect:generic/metric-pack:" +
            segment(packId)
        );
    }

    private String segment(String value) {
        String normalized = required(value, "asset key segment is required")
            .trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_.:-]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_+|_+$", "");
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(
                "asset key segment is required"
            );
        }
        return normalized;
    }

    private String boundedRemoteId(String type, String value) {
        String normalized = required(value, "remote asset id is required");
        if (normalized.length() <= 128) {
            return normalized;
        }
        return (
            segment(type) +
            ":" +
            UUID.nameUUIDFromBytes(
                normalized.getBytes(StandardCharsets.UTF_8)
            )
        );
    }

    private String required(Object value, String message) {
        String normalized = value == null
            ? null
            : String.valueOf(value).trim();
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String firstText(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }
}
