package com.yuzhi.dts.metrics.service;

import com.yuzhi.dts.metrics.domain.MetricModelVersion;
import com.yuzhi.dts.metrics.domain.repository.MetricModelStateRepository;
import com.yuzhi.dts.metrics.domain.repository.MetricModelVersionRepository;
import com.yuzhi.dts.metrics.service.dto.MetricContractErrorCode;
import com.yuzhi.dts.metrics.service.dto.MetricLifecycleStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Transactional boundary for the publish write step.
 *
 * <p>{@code MetricModelLifecycleService.publish} issues the remote platform release submit OUTSIDE any DB
 * transaction (so a {@code RestClient} call never holds a connection open), then delegates the version
 * insert + state update to this component so they run together in one short transaction. This is a
 * separate bean — not a self-invoked method on the service — so Spring's transactional proxy actually
 * applies (self-invocation would bypass it).
 *
 * <p>Concurrency: the {@code uk_metric_model_version (model_id, version)} unique constraint plus the
 * {@code @Version} optimistic lock serialize parallel publishes; the loser surfaces as an optimistic-lock
 * or integrity-violation failure which is mapped to HTTP 409 {@code metric_version_conflict} (T04).
 */
@Component
public class MetricLifecyclePublishWriter {

    private final MetricModelStateRepository modelStateRepository;
    private final MetricModelVersionRepository modelVersionRepository;

    public MetricLifecyclePublishWriter(
        MetricModelStateRepository modelStateRepository,
        MetricModelVersionRepository modelVersionRepository
    ) {
        this.modelStateRepository = modelStateRepository;
        this.modelVersionRepository = modelVersionRepository;
    }

    @Transactional
    public Map<String, Object> commitPublish(String modelId, Map<String, Object> state, Map<String, Object> submitted) {
        List<MetricModelVersion> existing = modelVersionRepository.findByModelIdOrderByVersionOrdinalAsc(modelId);
        int priorCount = existing.size();
        String version = "v" + (priorCount + 1);
        String previousVersion = existing.isEmpty() ? "" : existing.get(existing.size() - 1).getVersion();
        String publishedAt = Instant.now().toString();
        String platformPublishReference = firstText(submitted.get("publishReference"), submitted.get("id"), "platform-release://" + modelId);
        String releaseDecision = firstText(submitted.get("decision"), submitted.get("status"), "SUBMITTED");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelId", modelId);
        result.put("modelName", state.get("modelName"));
        result.put("status", MetricLifecycleStatus.PUBLISHED.code());
        result.put("artifactRef", state.get("artifactRef"));
        result.put("version", version);
        result.put("activeVersion", version);
        result.put("previousVersion", previousVersion);
        result.put("platformPublishReference", platformPublishReference);
        result.put("releaseDecision", releaseDecision);
        result.put("rollbackAvailable", priorCount > 0);
        result.put("publishedAt", publishedAt);

        try {
            MetricModelVersion versionRow = new MetricModelVersion();
            versionRow.setModelId(modelId);
            versionRow.setVersionOrdinal(priorCount + 1);
            versionRow.setVersion(version);
            versionRow.setModelName(text(state.get("modelName")));
            versionRow.setStatus(MetricLifecycleStatus.PUBLISHED.code());
            versionRow.setPlatformPublishReference(platformPublishReference);
            versionRow.setReleaseDecision(releaseDecision);
            versionRow.setArtifactRef(text(state.get("artifactRef")));
            versionRow.setPublishedAt(Instant.parse(publishedAt));
            modelVersionRepository.saveAndFlush(versionRow);
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException e) {
            // A parallel publish already inserted the same (model_id, version); the loser surfaces 409.
            throw new ResponseStatusException(HttpStatus.CONFLICT, MetricContractErrorCode.METRIC_VERSION_CONFLICT.code(), e);
        }

        MetricModelStateMapper.writeState(modelStateRepository, modelId, result);
        return result;
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private static String text(Object value) {
        return value != null ? String.valueOf(value).trim() : "";
    }
}
