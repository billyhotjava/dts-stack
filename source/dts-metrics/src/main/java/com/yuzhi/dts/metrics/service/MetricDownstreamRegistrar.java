package com.yuzhi.dts.metrics.service;

import com.yuzhi.dts.metrics.config.DtsMetricsProperties;
import com.yuzhi.dts.metrics.domain.repository.MetricModelStateRepository;
import com.yuzhi.dts.metrics.service.dto.MetricContractErrorCode;
import com.yuzhi.dts.metrics.service.dto.MetricLifecycleStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Closes the publish loop (Sprint-35b F3): after a model version is committed, register its metric-pack and
 * metric identities plus the platform BI Dataset + lineage facts so a published model is actually consumable
 * and governable downstream rather than only existing as a metrics-side {@code PUBLISHED} row.
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
     * registration fails. A no-op (returns the input unchanged) when registration is disabled.
     */
    public Map<String, Object> registerDownstream(
        String modelId,
        Map<String, Object> publishResult,
        Map<String, Object> graph
    ) {
        boolean registerBiLineage = properties
            .getPlatform()
            .isBiLineageRegisterEnabled();
        if (!registerBiLineage && !taggableAssetRegistrar.isEnabled()) {
            return publishResult;
        }
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
