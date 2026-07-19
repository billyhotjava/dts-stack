package com.yuzhi.dts.platform.service.modeling.warehouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.MetricRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService.GateStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService.GateView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService.Stage;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.EvidenceFreshness;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageCode;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageEvidence;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Uses live ModelSpec gates for build/release and the owner-written integration ledger for later stages. */
@Component
@Transactional(readOnly = true)
public class WarehousePlanDownstreamEvidenceAdapter implements WarehousePlanDownstreamEvidencePort {

    private static final Pattern INDICATOR_VERSION = Pattern.compile("(?i)^v?([1-9][0-9]*)$");

    private static final List<StageCode> RECORDED_STAGES = List.of(
        StageCode.DATA_ASSET,
        StageCode.DATA_SERVICE_OPERATIONS
    );

    private final ModelSpecStageGateService stageGates;
    private final GovIndicatorDefinitionRepository indicators;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public WarehousePlanDownstreamEvidenceAdapter(
        ModelSpecStageGateService stageGates,
        GovIndicatorDefinitionRepository indicators,
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper
    ) {
        this.stageGates = stageGates;
        this.indicators = indicators;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<StageEvidence> read(String tenantId, UUID planId, List<ModelSpecView> models) {
        List<StageEvidence> evidence = new ArrayList<>();
        evidence.add(buildEvidence(tenantId, models));
        evidence.add(metricEvidence(models));
        Map<StageCode, StageEvidence> recorded = latestRecorded(tenantId, planId);
        for (StageCode stage : RECORDED_STAGES) {
            StageEvidence item = recorded.get(stage);
            if (item != null) evidence.add(item);
        }
        return List.copyOf(evidence);
    }

    private StageEvidence metricEvidence(List<ModelSpecView> models) {
        List<ModelSpecView> eligible = models
            .stream()
            .filter(Objects::nonNull)
            .filter(model -> model.status() == ModelStatus.PUBLISHED)
            .filter(model ->
                model.modelType() == ModelType.FACT ||
                model.modelType() == ModelType.SUMMARY ||
                model.modelType() == ModelType.APPLICATION
            )
            .toList();
        if (eligible.isEmpty()) {
            return blocked(
                StageCode.METRIC_SYSTEM,
                "MODEL_SPEC_PUBLISHED_REQUIRED",
                "Publish a fact, summary or application model before metric handoff"
            );
        }
        int referenceCount = 0;
        try {
            for (ModelSpecView model : eligible) {
                if (model.metricRefs() == null || model.metricRefs().isEmpty()) {
                    return blocked(
                        StageCode.METRIC_SYSTEM,
                        "MODEL_METRIC_REF_REQUIRED",
                        "Bind at least one published indicator version to every published metric model"
                    );
                }
                for (MetricRef reference : model.metricRefs()) {
                    UUID indicatorId = parseIndicatorId(reference);
                    GovIndicatorDefinition indicator = indicatorId == null ? null : indicators.findById(indicatorId).orElse(null);
                    Integer currentVersion = indicator == null ? null : parseIndicatorVersion(indicator.getVersion());
                    if (
                        indicator == null ||
                        !"PUBLISHED".equalsIgnoreCase(indicator.getStatus()) ||
                        currentVersion == null ||
                        reference.version() != currentVersion
                    ) {
                        return new StageEvidence(
                            StageCode.METRIC_SYSTEM,
                            StageStatus.BLOCKED,
                            EvidenceFreshness.STALE,
                            referenceCount,
                            "MODEL_METRIC_REF_STALE",
                            "A model metric reference no longer matches a published indicator owner version",
                            null,
                            null
                        );
                    }
                    referenceCount++;
                }
            }
        } catch (RuntimeException unavailable) {
            return unknown(StageCode.METRIC_SYSTEM, "METRIC_OWNER_EVIDENCE_UNAVAILABLE");
        }
        return complete(StageCode.METRIC_SYSTEM, referenceCount);
    }

    private static UUID parseIndicatorId(MetricRef reference) {
        if (reference == null || reference.metricId() == null) return null;
        try {
            return UUID.fromString(reference.metricId());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static Integer parseIndicatorVersion(String value) {
        if (value == null) return null;
        Matcher matcher = INDICATOR_VERSION.matcher(value.trim());
        if (!matcher.matches()) return null;
        try {
            return Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private StageEvidence buildEvidence(String tenantId, List<ModelSpecView> models) {
        List<ModelSpecView> active = models
            .stream()
            .filter(Objects::nonNull)
            .filter(model -> model.status() != ModelStatus.ARCHIVED)
            .toList();
        if (active.isEmpty()) {
            return blocked(StageCode.BUILD_QUALITY_RELEASE, "MODEL_SPEC_REQUIRED", "Create a model before build and release");
        }
        int ready = 0;
        for (ModelSpecView model : active) {
            GateView release = stageGates
                .evaluateAll(tenantId, model.id())
                .stream()
                .filter(gate -> gate.stage() == Stage.RELEASE_READY)
                .findFirst()
                .orElse(null);
            if (release == null) {
                return unknown(StageCode.BUILD_QUALITY_RELEASE, "MODEL_RELEASE_EVIDENCE_UNAVAILABLE");
            }
            if (release.status() != GateStatus.READY) {
                ModelSpecStageGateService.GateBlocker first = release.blockers().stream().findFirst().orElse(null);
                return blocked(
                    StageCode.BUILD_QUALITY_RELEASE,
                    first == null ? "MODEL_RELEASE_NOT_READY" : first.code(),
                    first == null ? "Build, quality or release evidence is incomplete" : first.message()
                );
            }
            ready++;
        }
        return complete(StageCode.BUILD_QUALITY_RELEASE, ready);
    }

    private Map<StageCode, StageEvidence> latestRecorded(String tenantId, UUID planId) {
        List<RecordedEvidence> rows = jdbcTemplate.query(
            """
            select distinct on (stage_code) stage_code, status, freshness, evidence_json, computed_at
              from modeling_warehouse_plan_stage_evidence
             where tenant_id = ? and plan_id = ? and stage_code in ('DATA_ASSET', 'DATA_SERVICE_OPERATIONS')
             order by stage_code, computed_at desc nulls last, id desc
            """,
            WarehousePlanDownstreamEvidenceAdapter::mapRecorded,
            tenantId,
            planId
        );
        Map<StageCode, StageEvidence> result = new EnumMap<>(StageCode.class);
        for (RecordedEvidence row : rows) {
            StageEvidence evidence = toEvidence(row);
            if (evidence != null) result.put(evidence.stageCode(), evidence);
        }
        return result;
    }

    private StageEvidence toEvidence(RecordedEvidence row) {
        try {
            StageCode stage = StageCode.valueOf(row.stageCode());
            if (!RECORDED_STAGES.contains(stage)) return null;
            StageStatus status = StageStatus.valueOf(row.status());
            EvidenceFreshness freshness = EvidenceFreshness.valueOf(row.freshness());
            JsonNode details = row.evidenceJson() == null || row.evidenceJson().isBlank()
                ? objectMapper.createObjectNode()
                : objectMapper.readTree(row.evidenceJson());
            int count = Math.max(0, details.path("evidenceCount").asInt(0));
            String blockerCode = text(details, "blockerCode");
            String blockerMessage = text(details, "blockerMessage");
            return new StageEvidence(stage, status, freshness, count, blockerCode, blockerMessage, null, null);
        } catch (Exception exception) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static RecordedEvidence mapRecorded(ResultSet row, int rowNumber) throws SQLException {
        Timestamp computedAt = row.getTimestamp("computed_at");
        return new RecordedEvidence(
            row.getString("stage_code"),
            row.getString("status"),
            row.getString("freshness"),
            row.getString("evidence_json"),
            computedAt == null ? null : computedAt.toInstant()
        );
    }

    private static StageEvidence complete(StageCode stage, int count) {
        return new StageEvidence(stage, StageStatus.COMPLETE, EvidenceFreshness.CURRENT, count, null, null, null, null);
    }

    private static StageEvidence blocked(StageCode stage, String code, String message) {
        return new StageEvidence(stage, StageStatus.BLOCKED, EvidenceFreshness.CURRENT, 0, code, message, null, null);
    }

    private static StageEvidence unknown(StageCode stage, String code) {
        return new StageEvidence(
            stage,
            StageStatus.UNKNOWN,
            EvidenceFreshness.UNAVAILABLE,
            0,
            code,
            "The owning service could not provide current evidence",
            null,
            null
        );
    }

    private record RecordedEvidence(String stageCode, String status, String freshness, String evidenceJson, Instant computedAt) {}
}
