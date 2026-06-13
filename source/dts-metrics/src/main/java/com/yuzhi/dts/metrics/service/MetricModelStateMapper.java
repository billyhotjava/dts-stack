package com.yuzhi.dts.metrics.service;

import com.yuzhi.dts.metrics.domain.MetricModelState;
import com.yuzhi.dts.metrics.domain.MetricModelVersion;
import com.yuzhi.dts.metrics.domain.MetricRollbackEvent;
import com.yuzhi.dts.metrics.domain.repository.MetricModelStateRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lossless translation between the legacy heterogeneous {@code Map<String,Object>} lifecycle shapes and
 * the persisted entities.
 *
 * <p>{@link MetricModelState} stores the stable attributes in flat columns; every other key the legacy
 * {@code ConcurrentHashMap} carried per lifecycle phase is stashed in the {@code transient_state} jsonb
 * bucket so each read reassembles the exact same public Map (T04). Shared by both
 * {@link MetricModelLifecycleService} and {@link MetricLifecyclePublishWriter} so the persistence rules
 * live in one place.
 */
final class MetricModelStateMapper {

    private static final List<String> FLAT_STATE_KEYS = List.of(
        "modelId",
        "modelName",
        "status",
        "artifactRef",
        "artifacts",
        "appliedPolicySource",
        "appliedPredicateHash",
        "platformValidation"
    );

    private MetricModelStateMapper() {}

    /** Reassemble the legacy public-state Map from the flat columns plus the transient_state jsonb bucket. */
    static Map<String, Object> stateToMap(MetricModelState entity) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelId", entity.getModelId());
        result.put("modelName", entity.getModelName());
        result.put("status", entity.getStatus());
        result.put("artifactRef", entity.getArtifactRef());
        if (entity.getArtifacts() != null) {
            result.put("artifacts", entity.getArtifacts());
        }
        result.put("appliedPolicySource", entity.getAppliedPolicySource());
        result.put("appliedPredicateHash", entity.getAppliedPredicateHash());
        if (entity.getPlatformValidation() != null) {
            result.put("platformValidation", entity.getPlatformValidation());
        }
        Map<String, Object> transientState = entity.getTransientState();
        if (transientState != null) {
            result.putAll(transientState);
        }
        return result;
    }

    /**
     * Upsert the {@link MetricModelState} row from a public-state Map, splitting stable attributes into
     * flat columns and stashing the variant keys in {@code transient_state}.
     */
    static void writeState(MetricModelStateRepository repository, String modelId, Map<String, Object> state) {
        MetricModelState entity = repository.findById(modelId).orElseGet(MetricModelState::new);
        entity.setModelId(modelId);
        entity.setModelName(text(state.get("modelName")));
        entity.setStatus(text(state.get("status")));
        entity.setArtifactRef(textOrNull(state.get("artifactRef")));
        entity.setArtifacts(objectOrNull(state.get("artifacts")));
        entity.setAppliedPolicySource(textOrNull(state.get("appliedPolicySource")));
        entity.setAppliedPredicateHash(textOrNull(state.get("appliedPredicateHash")));
        entity.setPlatformValidation(objectOrNull(state.get("platformValidation")));
        entity.setActiveVersion(textOrNull(firstNonNull(state.get("activeVersion"), state.get("version"))));
        entity.setTransientState(transientState(state));
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
    }

    private static Map<String, Object> transientState(Map<String, Object> state) {
        Map<String, Object> bucket = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : state.entrySet()) {
            if (!FLAT_STATE_KEYS.contains(entry.getKey())) {
                bucket.put(entry.getKey(), entry.getValue());
            }
        }
        return bucket.isEmpty() ? null : bucket;
    }

    static List<Map<String, Object>> versionsToMaps(List<MetricModelVersion> versions) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (MetricModelVersion version : versions) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("version", version.getVersion());
            map.put("modelName", version.getModelName());
            map.put("status", version.getStatus());
            map.put("platformPublishReference", version.getPlatformPublishReference());
            map.put("releaseDecision", version.getReleaseDecision());
            map.put("publishedAt", version.getPublishedAt() != null ? version.getPublishedAt().toString() : null);
            map.put("artifactRef", version.getArtifactRef());
            result.add(map);
        }
        return result;
    }

    static List<Map<String, Object>> rollbackEventsToMaps(List<MetricRollbackEvent> events) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (MetricRollbackEvent event : events) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("version", event.getVersion());
            map.put("status", event.getStatus());
            map.put("rollbackFromVersion", event.getRollbackFromVersion());
            map.put("rollbackToVersion", event.getRollbackToVersion());
            map.put("platformRollbackReference", event.getPlatformRollbackReference());
            map.put("reason", event.getReason());
            map.put("rolledBackAt", event.getRolledBackAt() != null ? event.getRolledBackAt().toString() : null);
            result.add(map);
        }
        return result;
    }

    private static Map<String, Object> objectOrNull(Object value) {
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            return typed;
        }
        return null;
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String text(Object value) {
        return value != null ? String.valueOf(value).trim() : "";
    }

    private static String textOrNull(Object value) {
        return value != null ? String.valueOf(value).trim() : null;
    }
}
