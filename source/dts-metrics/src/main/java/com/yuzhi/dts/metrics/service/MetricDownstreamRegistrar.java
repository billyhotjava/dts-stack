package com.yuzhi.dts.metrics.service;

import com.yuzhi.dts.metrics.config.DtsMetricsProperties;
import com.yuzhi.dts.metrics.domain.repository.MetricModelStateRepository;
import com.yuzhi.dts.metrics.service.dto.MetricContractErrorCode;
import com.yuzhi.dts.metrics.service.dto.MetricLifecycleStatus;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Closes the publish loop (Sprint-35b F3): after a model version is committed, register its metric-pack and
 * metric identities, immutable classification inheritance, plus the platform BI Dataset + lineage facts so a
 * published model is actually consumable and governable downstream rather than only existing as a metrics-side
 * {@code PUBLISHED} row.
 *
 * <p>The taggable-asset receiver and the legacy BI/lineage receivers are independently gated by
 * {@code dts.metrics.platform.taggable-asset-register-enabled} and
 * {@code dts.metrics.platform.bi-lineage-register-enabled}. When an enabled registration fails, the model is
 * persisted as {@code PUBLISH_BLOCKED} — a committed version is never reported as a clean {@code PUBLISHED}
 * while its downstream registration is incomplete — and a 503 is surfaced for retry. The remote calls run
 * outside any DB transaction (the only DB write here is the single PUBLISH_BLOCKED state row); retry reuses
 * the committed version and the platform upsert contracts make the registrations idempotent.
 */
@Component
public class MetricDownstreamRegistrar {

    static final String GRAPH_SNAPSHOT_STATE_KEY = "graphSnapshot";

    private final PlatformContractClient platformContractClient;
    private final DtsMetricsProperties properties;
    private final MetricModelStateRepository modelStateRepository;
    private final MetricTaggableAssetRegistrar taggableAssetRegistrar;

    public MetricDownstreamRegistrar(
        PlatformContractClient platformContractClient,
        DtsMetricsProperties properties,
        MetricModelStateRepository modelStateRepository,
        MetricTaggableAssetRegistrar taggableAssetRegistrar
    ) {
        this.platformContractClient = platformContractClient;
        this.properties = properties;
        this.modelStateRepository = modelStateRepository;
        this.taggableAssetRegistrar = taggableAssetRegistrar;
    }

    /**
     * Register the just-published model with the platform BI Dataset + lineage. Returns the publish result
     * augmented with the registration references; throws (after persisting {@code PUBLISH_BLOCKED}) when a
     * registration or mandatory classification derivation fails.
     */
    public Map<String, Object> registerDownstream(
        String modelId,
        Map<String, Object> publishResult,
        Map<String, Object> graph
    ) {
        boolean registerBiLineage = properties
            .getPlatform()
            .isBiLineageRegisterEnabled();
        String modelName = text(publishResult.get("modelName"));
        String version = text(publishResult.get("version"));
        String artifactRef = text(publishResult.get("artifactRef"));
        String publishReference = text(publishResult.get("platformPublishReference"));
        try {
            Map<String, Object> registered = new LinkedHashMap<>(publishResult);
            registered.put(
                "status",
                MetricLifecycleStatus.PUBLISHED.code()
            );
            registered.remove("publishBlockReason");
            deriveClassifications(modelId, version, graph);
            if (taggableAssetRegistrar.isEnabled()) {
                Map<String, Object> taggable = taggableAssetRegistrar.register(
                    modelId,
                    publishResult,
                    graph
                );
                registered.put(
                    "taggableAssetRegistrationCount",
                    taggable.getOrDefault("registered", 0)
                );
            }
            if (registerBiLineage) {
                Map<String, Object> bi =
                    platformContractClient.registerBiDataset(
                        new PlatformContractClient.BiDatasetRegisterRequest(
                            modelId,
                            modelName,
                            version,
                            artifactRef,
                            publishReference
                        )
                    );
                Map<String, Object> lineage =
                    platformContractClient.registerLineage(
                        new PlatformContractClient.LineageRegisterRequest(
                            modelId,
                            modelName,
                            version,
                            artifactRef,
                            publishReference
                        )
                    );
                registered.put(
                    "biDatasetReference",
                    reference(bi, "datasetReference")
                );
                registered.put(
                    "lineageReference",
                    reference(lineage, "lineageReference")
                );
            }
            MetricModelStateMapper.writeState(modelStateRepository, modelId, registered);
            return registered;
        } catch (
            PlatformContractClient.PlatformContractException
            | IllegalArgumentException e
        ) {
            Map<String, Object> blocked = new LinkedHashMap<>(publishResult);
            blocked.put("status", MetricLifecycleStatus.PUBLISH_BLOCKED.code());
            if (graph != null && !graph.isEmpty()) {
                blocked.put(
                    GRAPH_SNAPSHOT_STATE_KEY,
                    new LinkedHashMap<>(graph)
                );
            }
            blocked.put(
                "publishBlockReason",
                "downstream asset registration failed"
            );
            MetricModelStateMapper.writeState(modelStateRepository, modelId, blocked);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, MetricContractErrorCode.PLATFORM_CONTRACT_UNAVAILABLE.code(), e);
        }
    }

    public boolean isTaggableAssetRegistrationEnabled() {
        return taggableAssetRegistrar.isEnabled();
    }

    /**
     * Idempotent classification-only adapter for Sprint-72 legacy backfill.
     * It intentionally does not republish artifacts or mutate lifecycle state.
     */
    public void backfillClassifications(
        String modelId,
        String version,
        Map<String, Object> graph
    ) {
        deriveClassifications(modelId, version, graph);
    }

    private void deriveClassifications(String modelId, String version, Map<String, Object> graph) {
        String base = required(graph == null ? null : graph.get("base"), "metric base asset is required");
        String baseType = firstText(
            graph == null ? null : graph.get("baseSubjectType"),
            graph == null ? null : graph.get("base_subject_type"),
            "ASSET"
        ).toUpperCase(Locale.ROOT);
        String tenant = firstText(
            graph == null ? null : graph.get("tenantNamespace"),
            graph == null ? null : graph.get("tenant_namespace"),
            graph == null ? null : graph.get("tenant"),
            "default"
        );
        String packId = firstText(
            graph == null ? null : graph.get("packId"),
            graph == null ? null : graph.get("pack_id"),
            modelId
        );
        String manualFloor = firstText(
            graph == null ? null : graph.get("classification"),
            graph == null ? null : graph.get("securityLevel"),
            graph == null ? null : graph.get("security_level")
        );
        List<PlatformContractClient.ConsumerClassificationSubjectRef> upstreams = List.of(
            new PlatformContractClient.ConsumerClassificationSubjectRef(baseType, base)
        );
        String originRef = "dts-metrics:model:" + modelId + ":version:" + version;
        platformContractClient.deriveConsumerClassification(
            new PlatformContractClient.ConsumerClassificationRequest(
                "METRIC",
                metricPackKey(tenant, packId, version),
                manualFloor,
                upstreams,
                originRef
            )
        );
        for (String code : metricCodes(graph)) {
            platformContractClient.deriveConsumerClassification(
                new PlatformContractClient.ConsumerClassificationRequest(
                    "METRIC",
                    metricKey(tenant, packId, code),
                    manualFloor,
                    upstreams,
                    originRef
                )
            );
        }
    }

    private static LinkedHashSet<String> metricCodes(Map<String, Object> graph) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        if (graph == null) {
            return codes;
        }
        addCodes(codes, graph.get("measures"));
        Object derived = graph.get("derived_metrics");
        if (derived instanceof List<?> values) {
            for (Object value : values) {
                if (value instanceof Map<?, ?> item) {
                    addCode(codes, firstText(item.get("id"), item.get("code"), item.get("name")));
                } else {
                    addCode(codes, text(value));
                }
            }
        }
        return codes;
    }

    private static void addCodes(LinkedHashSet<String> codes, Object raw) {
        if (!(raw instanceof List<?> values)) {
            return;
        }
        for (Object value : values) {
            if (value instanceof Map<?, ?> item) {
                addCode(codes, firstText(item.get("id"), item.get("code"), item.get("name")));
            } else {
                addCode(codes, text(value));
            }
        }
    }

    private static void addCode(LinkedHashSet<String> codes, String value) {
        if (StringUtils.hasText(value)) {
            codes.add(segment(value));
        }
    }

    private static String metricKey(String tenant, String packId, String metricCode) {
        return metricScope(tenant, packId) + "/metric:" + segment(metricCode);
    }

    private static String metricPackKey(String tenant, String packId, String version) {
        return metricScope(tenant, packId) + "/version:" + segment(required(version, "metric version is required"));
    }

    private static String metricScope(String tenant, String packId) {
        return (
            "tenant:" +
            segment(required(tenant, "tenant namespace is required")) +
            "/env:prod/dialect:generic/metric-pack:" +
            segment(required(packId, "metric pack id is required"))
        );
    }

    private static String segment(String value) {
        String normalized = required(value, "asset key segment is required")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_.:-]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_+|_+$", "");
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("asset key segment is required");
        }
        return normalized;
    }

    private static String required(Object value, String message) {
        String normalized = text(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String firstText(Object... values) {
        if (values != null) {
            for (Object value : values) {
                String candidate = text(value);
                if (StringUtils.hasText(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static String reference(Map<String, Object> response, String preferredKey) {
        Object preferred = response.get(preferredKey);
        if (preferred != null && StringUtils.hasText(String.valueOf(preferred))) {
            return String.valueOf(preferred).trim();
        }
        Object id = response.get("id");
        return id != null ? String.valueOf(id).trim() : null;
    }

    private static String text(Object value) {
        return value != null ? String.valueOf(value).trim() : "";
    }
}
