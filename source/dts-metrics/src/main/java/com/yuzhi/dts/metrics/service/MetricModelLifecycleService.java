package com.yuzhi.dts.metrics.service;

import com.yuzhi.dts.metrics.config.DtsMetricsProperties;
import com.yuzhi.dts.metrics.domain.MetricRollbackEvent;
import com.yuzhi.dts.metrics.domain.repository.MetricModelStateRepository;
import com.yuzhi.dts.metrics.domain.repository.MetricModelVersionRepository;
import com.yuzhi.dts.metrics.domain.repository.MetricRollbackEventRepository;
import com.yuzhi.dts.metrics.service.dto.MetricContractErrorCode;
import com.yuzhi.dts.metrics.service.dto.MetricLifecycleStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Durable metric-model lifecycle service.
 *
 * <p>The three legacy {@code ConcurrentHashMap}s (model state / versions / rollback events) are replaced
 * by JPA repositories (T04). Every public method keeps its exact signature and reassembles the legacy
 * {@code Map<String,Object>} return shape losslessly from the flat entity columns plus the
 * {@code transient_state} jsonb bucket on {@link MetricModelState}. Remote platform calls are issued
 * outside any write transaction so a {@code RestClient} call never holds a DB connection open.
 */
@Service
public class MetricModelLifecycleService {

    private static final Logger LOG = LoggerFactory.getLogger(MetricModelLifecycleService.class);

    private final MetricGraphDraftService graphDraftService;
    private final PlatformContractClient platformContractClient;
    private final MetricSecurityPolicyService securityPolicyService;
    private final MetricCandidateArtifactBuilder artifactBuilder;
    private final DtsMetricsProperties properties;
    private final MetricModelStateRepository modelStateRepository;
    private final MetricModelVersionRepository modelVersionRepository;
    private final MetricRollbackEventRepository rollbackEventRepository;
    private final MetricLifecyclePublishWriter publishWriter;
    private final MetricDownstreamRegistrar downstreamRegistrar;

    public MetricModelLifecycleService(
        MetricGraphDraftService graphDraftService,
        PlatformContractClient platformContractClient,
        MetricSecurityPolicyService securityPolicyService,
        MetricCandidateArtifactBuilder artifactBuilder,
        DtsMetricsProperties properties,
        MetricModelStateRepository modelStateRepository,
        MetricModelVersionRepository modelVersionRepository,
        MetricRollbackEventRepository rollbackEventRepository,
        MetricLifecyclePublishWriter publishWriter,
        MetricDownstreamRegistrar downstreamRegistrar
    ) {
        this.graphDraftService = graphDraftService;
        this.platformContractClient = platformContractClient;
        this.securityPolicyService = securityPolicyService;
        this.artifactBuilder = artifactBuilder;
        this.properties = properties;
        this.modelStateRepository = modelStateRepository;
        this.modelVersionRepository = modelVersionRepository;
        this.rollbackEventRepository = rollbackEventRepository;
        this.publishWriter = publishWriter;
        this.downstreamRegistrar = downstreamRegistrar;
    }

    @Transactional
    public Map<String, Object> generateArtifacts(String modelId, Map<String, Object> request) {
        return generateArtifacts(modelId, request, MetricSecurityPolicyService.SecurityActor.system());
    }

    /**
     * Generate candidate artifacts. The graph-lifecycle path now enforces the SAME platform security spine
     * as the metric-pack path (F2-T02/T03): source-asset permission is required, the real RLS/masking policy
     * is resolved from the platform (replacing the former {@code platform-policy-required} placeholder and
     * empty predicate hash), and that policy is injected into the generated SQL.
     */
    @Transactional
    public Map<String, Object> generateArtifacts(String modelId, Map<String, Object> request, MetricSecurityPolicyService.SecurityActor actor) {
        Map<String, Object> graph = graph(modelId, request);
        Map<String, Object> preflight = graphDraftService.preflightDraft(graph);
        if (!"GRAPH_READY".equals(preflight.get("status"))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MetricContractErrorCode.GRAPH_VALIDATION_FAILED.code());
        }
        String modelName = safeModelName(firstText(value(request, "modelName"), "dws_" + MetricIdentifiers.safeIdentifier(modelId) + "_summary"));
        PlatformContractClient.RlsPolicyResult policy = enforceSourcePolicy(actor, graph);
        String policySource = StringUtils.hasText(policy.policySource()) ? policy.policySource() : "platform-permission";
        String predicateHash = securityPolicyService.predicateHash(policy);
        Map<String, Object> artifacts;
        try {
            artifacts = artifactBuilder.build(modelName, graph, policy, policySource, predicateHash);
        } catch (IllegalArgumentException e) {
            // e.g. a platform-masked column used inside a metric formula (F2-T03 parity with the pack path).
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MetricContractErrorCode.GRAPH_VALIDATION_FAILED.code(), e);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelId", modelId);
        result.put("modelName", modelName);
        result.put("status", MetricLifecycleStatus.ARTIFACT_GENERATED.code());
        result.put("artifactRef", "candidate://dts-metrics/" + modelName);
        result.put("graphStatus", preflight.get("status"));
        result.put("diagnostics", preflight.get("diagnostics"));
        result.put("artifacts", artifacts);
        result.put("appliedPolicySource", policySource);
        result.put("appliedPredicateHash", predicateHash);
        result.put("warnings", List.of("Candidate artifacts must pass platform/dbt validation before review or publish."));
        result.put("meta", Map.of("generatedAt", Instant.now().toString(), "source", "dts-metrics model lifecycle"));
        saveState(modelId, result);
        emitAudit("metric.model.artifacts.generate", result, "ARTIFACT_GENERATED", false);
        return result;
    }

    /**
     * Require permission on the source asset and resolve its effective RLS/masking policy from the platform.
     * Permission denial maps to 403; platform transport failure maps to 503 (fail-closed: no artifact is
     * generated without a resolved policy).
     */
    private PlatformContractClient.RlsPolicyResult enforceSourcePolicy(MetricSecurityPolicyService.SecurityActor actor, Map<String, Object> graph) {
        MetricSecurityPolicyService.PolicyAsset asset = sourcePolicyAsset(graph);
        try {
            securityPolicyService.requirePermission(actor, asset, "PREVIEW");
            return securityPolicyService.resolvePolicy(actor, asset, "PREVIEW");
        } catch (MetricSecurityPolicyService.PermissionDeniedException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, MetricContractErrorCode.ASSET_PERMISSION_DENIED.code(), e);
        } catch (PlatformContractClient.PlatformContractException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, MetricContractErrorCode.PLATFORM_CONTRACT_UNAVAILABLE.code(), e);
        }
    }

    private static MetricSecurityPolicyService.PolicyAsset sourcePolicyAsset(Map<String, Object> graph) {
        String base = text(graph.get("base"));
        if (!StringUtils.hasText(base)) {
            return null;
        }
        return new MetricSecurityPolicyService.PolicyAsset("DBT_MODEL", base, base, firstText(graph.get("classification"), "INTERNAL"));
    }

    public Map<String, Object> validateModel(String modelId, Map<String, Object> request) {
        // Load (and lazily generate) artifact state inside a write tx, then call the platform OUTSIDE the
        // tx so the remote round-trip never holds a DB connection open. The validated result is persisted
        // in a final short write tx.
        Map<String, Object> state = artifactState(modelId, request);
        try {
            Map<String, Object> validation = platformContractClient.validateMetricModel(
                new PlatformContractClient.MetricModelValidationRequest(
                    modelId,
                    text(state.get("modelName")),
                    text(state.get("artifactRef")),
                    graph(modelId, request),
                    object(state.get("artifacts")),
                    text(state.get("appliedPolicySource")),
                    text(state.get("appliedPredicateHash"))
                )
            );
            if (!platformPassed(validation)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MetricContractErrorCode.DBT_VALIDATION_FAILED.code());
            }
            Map<String, Object> next = new LinkedHashMap<>(state);
            next.put("status", MetricLifecycleStatus.DBT_VALIDATED.code());
            next.put("platformValidation", validation);
            next.put("validatedAt", Instant.now().toString());
            saveState(modelId, next);
            emitAudit("metric.model.validate", next, "DBT_VALIDATED", false);
            return next;
        } catch (PlatformContractClient.PlatformContractException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, MetricContractErrorCode.PLATFORM_CONTRACT_UNAVAILABLE.code(), e);
        }
    }

    @Transactional
    public Map<String, Object> submitReview(String modelId) {
        Map<String, Object> state = requireStatus(modelId, MetricLifecycleStatus.DBT_VALIDATED.code());
        Map<String, Object> next = publicState(state, MetricLifecycleStatus.REVIEW_SUBMITTED.code());
        next.put("reviewReference", "platform-review://" + modelId);
        next.put("submittedAt", Instant.now().toString());
        saveState(modelId, next);
        return next;
    }

    public Map<String, Object> publishDryRun(String modelId) {
        // Read-validate state, call the release gate OUTSIDE a write tx, then persist the dry-run result.
        Map<String, Object> state = requireStatus(modelId, MetricLifecycleStatus.DBT_VALIDATED.code());
        try {
            Map<String, Object> releaseGate = platformContractClient.checkDbtReleaseGate(
                new PlatformContractClient.DbtReleaseGateRequest(
                    text(state.get("modelName")),
                    null,
                    null,
                    true,
                    text(state.get("appliedPolicySource")),
                    text(state.get("appliedPredicateHash"))
                )
            );
            Map<String, Object> next = publicState(state, MetricLifecycleStatus.PUBLISH_DRY_RUN_READY.code());
            next.put("releaseGate", releaseGate);
            next.put("checkedAt", Instant.now().toString());
            saveState(modelId, next);
            return next;
        } catch (PlatformContractClient.PlatformContractException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, MetricContractErrorCode.PLATFORM_CONTRACT_UNAVAILABLE.code(), e);
        }
    }

    public Map<String, Object> publish(String modelId) {
        // Idempotent retry: a model already PUBLISHED at the current version returns its existing result
        // instead of submitting a duplicate release (F3). A new version still requires re-validation first.
        Map<String, Object> published = loadState(modelId);
        if (published != null && MetricLifecycleStatus.PUBLISHED.code().equals(text(published.get("status")))) {
            return published;
        }
        Map<String, Object> state = requireStatus(
            modelId,
            MetricLifecycleStatus.DBT_VALIDATED.code(),
            MetricLifecycleStatus.PUBLISH_DRY_RUN_READY.code(),
            MetricLifecycleStatus.REVIEW_SUBMITTED.code()
        );
        try {
            // Remote submit happens OUTSIDE the write tx; the version insert + state update run in a single
            // short tx where the unique(model_id, version) constraint + @Version serialize concurrent
            // publishes (loser maps to 409 metric_version_conflict).
            Map<String, Object> submitted = platformContractClient.submitDbtRelease(
                new PlatformContractClient.DbtReleaseSubmitRequest(
                    modelId,
                    text(state.get("modelName")),
                    text(state.get("artifactRef")),
                    false,
                    text(state.get("appliedPolicySource")),
                    text(state.get("appliedPredicateHash"))
                )
            );
            // Persist in a dedicated transactional bean so the proxy applies (self-invocation would not):
            // the version insert + state update run together in one short tx.
            Map<String, Object> result = publishWriter.commitPublish(modelId, state, submitted);
            // Close the publish loop: register the version with platform BI Dataset + lineage (F3). When the
            // registration is enabled and fails, the model is persisted as PUBLISH_BLOCKED and a 503 surfaces.
            result = downstreamRegistrar.registerDownstream(modelId, result);
            emitAudit("metric.model.publish", result, "PUBLISHED", true);
            return result;
        } catch (PlatformContractClient.PlatformContractException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, MetricContractErrorCode.PLATFORM_CONTRACT_UNAVAILABLE.code(), e);
        }
    }

    @Transactional
    public Map<String, Object> rollback(String modelId, Map<String, Object> request) {
        Map<String, Object> state = currentState(modelId);
        List<Map<String, Object>> versions = mappedVersions(modelId);
        if (versions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MetricContractErrorCode.PUBLISHED_VERSION_REQUIRED.code());
        }
        String fromVersion = firstText(state.get("activeVersion"), state.get("version"), latestVersion(versions));
        Map<String, Object> target = rollbackTarget(versions, text(value(request, "targetVersion")), fromVersion);
        String rollbackToVersion = text(target.get("version"));
        int eventOrdinal = (int) (rollbackEventRepository.countByModelId(modelId) + 1);
        String eventVersion = "rollback-" + eventOrdinal;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelId", modelId);
        result.put("modelName", target.get("modelName"));
        result.put("status", MetricLifecycleStatus.ROLLED_BACK.code());
        result.put("version", eventVersion);
        result.put("activeVersion", rollbackToVersion);
        result.put("rollbackFromVersion", fromVersion);
        result.put("rollbackToVersion", rollbackToVersion);
        result.put("platformPublishReference", target.get("platformPublishReference"));
        result.put("platformRollbackReference", "platform-rollback://" + modelId + "/" + eventVersion);
        result.put("releaseDecision", target.get("releaseDecision"));
        result.put("reason", firstText(value(request, "reason"), "manual rollback"));
        result.put("rollbackPlan", List.of("freeze current publish reference", "restore semantic graph/artifact pointer", "request BI consumer refresh"));
        result.put("consumerLockImpact", List.of("BI Dataset consumers must refresh against rollbackToVersion before the next publish."));
        result.put("rolledBackAt", Instant.now().toString());
        saveState(modelId, result);
        appendRollbackEvent(modelId, eventOrdinal, result);
        emitAudit("metric.model.rollback", result, "ROLLED_BACK", true);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> versionHistory(String modelId) {
        Map<String, Object> state = currentState(modelId);
        List<Map<String, Object>> versions = mappedVersions(modelId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelId", modelId);
        result.put("modelName", state.get("modelName"));
        result.put("status", state.get("status"));
        result.put("activeVersion", firstText(state.get("activeVersion"), state.get("version")));
        result.put("versions", versions);
        result.put("rollbackEvents", mappedRollbackEvents(modelId));
        result.put("rollbackAvailable", versions.size() > 1);
        return result;
    }

    private Map<String, Object> artifactState(String modelId, Map<String, Object> request) {
        Map<String, Object> current = loadState(modelId);
        if (current != null && current.containsKey("artifacts") && current.get("artifacts") != null) {
            return current;
        }
        return generateArtifacts(modelId, request);
    }

    private Map<String, Object> requireStatus(String modelId, String... allowed) {
        Map<String, Object> state = loadState(modelId);
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MetricContractErrorCode.ARTIFACT_REQUIRED.code());
        }
        String status = text(state.get("status"));
        for (String item : allowed) {
            if (item.equals(status)) {
                return state;
            }
        }
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MetricContractErrorCode.DBT_VALIDATION_REQUIRED.code());
    }

    private Map<String, Object> currentState(String modelId) {
        Map<String, Object> state = loadState(modelId);
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, MetricContractErrorCode.MODEL_LIFECYCLE_STATE_NOT_FOUND.code());
        }
        return state;
    }

    private Map<String, Object> publicState(Map<String, Object> state, String status) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelId", state.get("modelId"));
        result.put("modelName", state.get("modelName"));
        result.put("status", status);
        result.put("artifactRef", state.get("artifactRef"));
        result.put("appliedPolicySource", state.get("appliedPolicySource"));
        result.put("appliedPredicateHash", state.get("appliedPredicateHash"));
        return result;
    }

    // ---- State persistence + Map<->entity round-trip (delegated to MetricModelStateMapper) ----

    /**
     * Load the current public-state {@code Map} for a model, reassembling the legacy heterogeneous shape
     * from the flat columns plus the {@code transient_state} jsonb bucket. Returns {@code null} when no row
     * exists (callers map that to the appropriate error/404).
     */
    private Map<String, Object> loadState(String modelId) {
        return modelStateRepository.findById(modelId).map(MetricModelStateMapper::stateToMap).orElse(null);
    }

    private void saveState(String modelId, Map<String, Object> state) {
        MetricModelStateMapper.writeState(modelStateRepository, modelId, state);
    }

    /**
     * Emit a platform audit event for a high-risk lifecycle action (F2-T04). Gated by
     * {@code dts.metrics.platform.audit-events-enabled} (off by default until the platform
     * {@code /internal/audit-events} endpoint is live — F3-T03 联调依赖). Failures are never swallowed
     * silently: blocking actions (publish/rollback) log at ERROR for reconciliation, non-blocking actions
     * (generate/validate) log at WARN. Transactional audit ordering and compensation is F3-T01's saga.
     */
    private void emitAudit(String action, Map<String, Object> state, String outcome, boolean blocking) {
        if (!properties.getPlatform().isAuditEventsEnabled()) {
            return;
        }
        try {
            platformContractClient.recordAuditEvent(
                new PlatformContractClient.AuditEventRequest(
                    action,
                    text(state.get("modelId")),
                    text(state.get("modelName")),
                    "system",
                    textOrNull(state.get("appliedPolicySource")),
                    textOrNull(state.get("appliedPredicateHash")),
                    firstTextOrNull(state.get("platformPublishReference"), state.get("platformRollbackReference"), state.get("artifactRef")),
                    outcome,
                    Instant.now().toString()
                )
            );
        } catch (PlatformContractClient.PlatformContractException e) {
            if (blocking) {
                LOG.error("audit event '{}' for model {} FAILED after a high-risk action; manual reconciliation may be required", action, text(state.get("modelId")), e);
            } else {
                LOG.warn("audit event '{}' for model {} failed; continuing (non-blocking)", action, text(state.get("modelId")), e);
            }
        }
    }

    private List<Map<String, Object>> mappedVersions(String modelId) {
        return MetricModelStateMapper.versionsToMaps(modelVersionRepository.findByModelIdOrderByVersionOrdinalAsc(modelId));
    }

    private List<Map<String, Object>> mappedRollbackEvents(String modelId) {
        return MetricModelStateMapper.rollbackEventsToMaps(rollbackEventRepository.findByModelIdOrderByEventOrdinalAsc(modelId));
    }

    private void appendRollbackEvent(String modelId, int eventOrdinal, Map<String, Object> state) {
        MetricRollbackEvent event = new MetricRollbackEvent();
        event.setModelId(modelId);
        event.setEventOrdinal(eventOrdinal);
        event.setVersion(text(state.get("version")));
        event.setStatus(text(state.get("status")));
        event.setRollbackFromVersion(textOrNull(state.get("rollbackFromVersion")));
        event.setRollbackToVersion(textOrNull(state.get("rollbackToVersion")));
        event.setPlatformRollbackReference(textOrNull(state.get("platformRollbackReference")));
        event.setReason(textOrNull(state.get("reason")));
        Object rolledBackAt = state.get("rolledBackAt");
        event.setRolledBackAt(rolledBackAt != null ? Instant.parse(String.valueOf(rolledBackAt)) : null);
        rollbackEventRepository.save(event);
    }

    private static String latestVersion(List<Map<String, Object>> versions) {
        return versions.isEmpty() ? "" : text(versions.get(versions.size() - 1).get("version"));
    }

    private Map<String, Object> rollbackTarget(List<Map<String, Object>> versions, String requestedVersion, String fromVersion) {
        if (StringUtils.hasText(requestedVersion)) {
            for (Map<String, Object> version : versions) {
                if (requestedVersion.equals(text(version.get("version")))) {
                    if (requestedVersion.equals(fromVersion)) {
                        throw new ResponseStatusException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            MetricContractErrorCode.ROLLBACK_TARGET_MUST_DIFFER.code()
                        );
                    }
                    return version;
                }
            }
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MetricContractErrorCode.ROLLBACK_TARGET_NOT_FOUND.code());
        }
        int currentIndex = versions.size() - 1;
        for (int i = 0; i < versions.size(); i++) {
            if (fromVersion.equals(text(versions.get(i).get("version")))) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MetricContractErrorCode.ROLLBACK_TARGET_REQUIRED.code());
        }
        return versions.get(currentIndex - 1);
    }

    private Map<String, Object> graph(String modelId, Map<String, Object> request) {
        Object graph = value(request, "graph");
        if (graph instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            return typed;
        }
        return graphDraftService.graph(modelId);
    }

    private static boolean platformPassed(Map<String, Object> validation) {
        Object decision = validation.get("decision");
        Object status = validation.get("status");
        Object valid = validation.get("valid");
        return Boolean.TRUE.equals(valid) || "PASS".equals(decision) || MetricLifecycleStatus.DBT_VALIDATED.code().equals(status);
    }

    private static String safeModelName(String value) {
        String modelName = MetricIdentifiers.safeIdentifier(value).toLowerCase(Locale.ROOT);
        if (!modelName.startsWith("dws_") && !modelName.startsWith("ads_")) {
            return "dws_" + modelName;
        }
        return modelName;
    }

    private static Object value(Map<String, Object> request, String key) {
        return request != null ? request.get(key) : null;
    }

    private static Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            return typed;
        }
        return Map.of();
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private static String firstTextOrNull(Object... values) {
        String text = firstText(values);
        return StringUtils.hasText(text) ? text : null;
    }

    private static String text(Object value) {
        return value != null ? String.valueOf(value).trim() : "";
    }

    private static String textOrNull(Object value) {
        return value != null ? String.valueOf(value).trim() : null;
    }
}
