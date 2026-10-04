package com.yuzhi.dts.platform.service.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

@Service
@Transactional(readOnly = true)
public class CatalogLifecycleProjectionService {

    private static final List<String> STAGES = List.of("CREATE", "STORAGE", "USE", "SHARE", "ARCHIVE", "DESTROY");

    private final JdbcTemplate jdbcTemplate;
    private final CatalogDatasetRepository datasetRepository;
    private final CatalogClassificationService classificationService;
    private final AccessChecker accessChecker;
    private final ObjectMapper objectMapper;

    public CatalogLifecycleProjectionService(
        JdbcTemplate jdbcTemplate,
        CatalogDatasetRepository datasetRepository,
        CatalogClassificationService classificationService,
        AccessChecker accessChecker,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.datasetRepository = datasetRepository;
        this.classificationService = classificationService;
        this.accessChecker = accessChecker;
        this.objectMapper = objectMapper;
    }

    public LifecycleView project(UUID datasetId, String activeDept) {
        CatalogDataset dataset = datasetRepository
            .findById(datasetId)
            .orElseThrow(() -> new IllegalArgumentException("Dataset does not exist: " + datasetId));
        if (!accessChecker.canRead(dataset) || !accessChecker.departmentAllowed(dataset, activeDept)) {
            throw new AccessDeniedException("无权查看该资产生命周期");
        }
        CatalogClassificationSnapshot seal = classificationService
            .resolve("ASSET", CatalogAssetKey.dataset(dataset))
            .orElse(null);
        List<EventView> events = new ArrayList<>();
        events.addAll(controlEvents(datasetId));
        events.addAll(legacyLifecycleEvents(datasetId));
        events.addAll(accessRequestEvents(datasetId));
        events.addAll(grantEvents(datasetId));
        events.sort(
            Comparator.comparing(EventView::occurredAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(EventView::eventId)
        );

        Map<String, StageView> stages = new LinkedHashMap<>();
        for (String stage : STAGES) {
            EventView latest = events
                .stream()
                .filter(event -> stage.equals(event.stage()))
                .max(
                    Comparator.comparing(EventView::occurredAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(EventView::eventId)
                )
                .orElse(null);
            stages.put(
                stage,
                new StageView(
                    stage,
                    latest == null ? defaultStatus(stage, dataset) : latest.status(),
                    latest == null ? null : latest.requestSource(),
                    latest == null ? null : latest.requestRef(),
                    latest == null ? null : latest.actor(),
                    latest == null ? null : latest.occurredAt(),
                    latest == null ? Map.of() : latest.evidence()
                )
            );
        }
        return new LifecycleView(
            dataset.getId(),
            dataset.getName(),
            dataset.getOwnerDept(),
            dataset.getLifecycleStatus(),
            Boolean.TRUE.equals(dataset.getEnabled()),
            seal == null ? null : seal.getId(),
            seal == null || seal.getRecordVersion() == null ? 0L : seal.getRecordVersion(),
            seal == null ? dataset.getClassification() : seal.getEffectiveLevel(),
            List.copyOf(stages.values()),
            List.copyOf(events),
            trash(datasetId),
            destructionProofs(datasetId)
        );
    }

    private List<EventView> controlEvents(UUID datasetId) {
        return jdbcTemplate.query(
            """
            select id, stage, event_type, status, request_source, request_ref,
                   seal_id, seal_version, effective_level, data_volume,
                   evidence_json::text, actor, occurred_at
              from catalog_lifecycle_event
             where dataset_id=?
             order by occurred_at, id
            """,
            (rs, rowNum) ->
                new EventView(
                    rs.getObject("id", UUID.class).toString(),
                    rs.getString("stage"),
                    rs.getString("event_type"),
                    rs.getString("status"),
                    rs.getString("request_source"),
                    rs.getString("request_ref"),
                    rs.getObject("seal_id", UUID.class),
                    rs.getObject("seal_version") == null ? null : rs.getLong("seal_version"),
                    rs.getString("effective_level"),
                    rs.getObject("data_volume") == null ? null : rs.getLong("data_volume"),
                    parseMap(rs.getString("evidence_json")),
                    rs.getString("actor"),
                    instant(rs, "occurred_at")
                ),
            datasetId
        );
    }

    private List<EventView> legacyLifecycleEvents(UUID datasetId) {
        return jdbcTemplate.query(
            """
            select id, request_type, status, notes, requested_lifecycle_status,
                   requested_retention_days, requested_expires_at,
                   decided_by, decided_at, created_by, created_date
              from catalog_lifecycle_request
             where dataset_id=?
            """,
            (rs, rowNum) -> {
                String type = rs.getString("request_type");
                String stage = "ARCHIVE".equalsIgnoreCase(type)
                    ? "ARCHIVE"
                    : ("DISPOSE".equalsIgnoreCase(type) ? "DESTROY" : "STORAGE");
                Map<String, Object> evidence = new LinkedHashMap<>();
                put(evidence, "notes", rs.getString("notes"));
                put(evidence, "requestedLifecycleStatus", rs.getString("requested_lifecycle_status"));
                put(evidence, "retentionDays", rs.getObject("requested_retention_days"));
                put(evidence, "expiresAt", instant(rs, "requested_expires_at"));
                return new EventView(
                    "legacy-lifecycle:" + rs.getObject("id", UUID.class),
                    stage,
                    "LEGACY_" + type + "_REQUEST",
                    rs.getString("status"),
                    "CATALOG_LIFECYCLE_REQUEST",
                    String.valueOf(rs.getObject("id", UUID.class)),
                    null,
                    null,
                    null,
                    null,
                    Map.copyOf(evidence),
                    rs.getString("decided_by") == null ? rs.getString("created_by") : rs.getString("decided_by"),
                    instant(rs, "decided_at") == null ? instant(rs, "created_date") : instant(rs, "decided_at")
                );
            },
            datasetId
        );
    }

    private List<EventView> accessRequestEvents(UUID datasetId) {
        return jdbcTemplate.query(
            """
            select id, status, can_query, can_preview, valid_from, valid_to,
                   requester_username, target_username, reason, decided_by,
                   decided_at, created_date
              from catalog_dataset_access_request
             where dataset_id=?
            """,
            (rs, rowNum) ->
                new EventView(
                    "access-request:" + rs.getObject("id", UUID.class),
                    "USE",
                    "DATA_ACCESS_REQUEST",
                    rs.getString("status"),
                    "DATA_ACCESS_APPROVAL",
                    String.valueOf(rs.getObject("id", UUID.class)),
                    null,
                    null,
                    null,
                    null,
                    Map.of(
                        "canQuery",
                        rs.getBoolean("can_query"),
                        "canPreview",
                        rs.getBoolean("can_preview"),
                        "target",
                        String.valueOf(rs.getString("target_username")),
                        "validFrom",
                        String.valueOf(instant(rs, "valid_from")),
                        "validTo",
                        String.valueOf(instant(rs, "valid_to")),
                        "reason",
                        String.valueOf(rs.getString("reason"))
                    ),
                    rs.getString("decided_by") == null ? rs.getString("requester_username") : rs.getString("decided_by"),
                    instant(rs, "decided_at") == null ? instant(rs, "created_date") : instant(rs, "decided_at")
                ),
            datasetId
        );
    }

    private List<EventView> grantEvents(UUID datasetId) {
        Instant now = Instant.now();
        return jdbcTemplate.query(
            """
            select id, grant_type, can_query, can_preview, valid_from, valid_to,
                   grantee_username, source_request_id, created_by, created_date
              from catalog_dataset_grant
             where dataset_id=?
            """,
            (rs, rowNum) -> {
                Instant validTo = instant(rs, "valid_to");
                return new EventView(
                    "dataset-grant:" + rs.getObject("id", UUID.class),
                    "SHARE".equalsIgnoreCase(rs.getString("grant_type")) ? "SHARE" : "USE",
                    "GRANT",
                    validTo != null && validTo.isBefore(now) ? "EXPIRED" : "ACTIVE",
                    "CATALOG_DATASET_GRANT",
                    String.valueOf(rs.getObject("id", UUID.class)),
                    null,
                    null,
                    null,
                    null,
                    Map.of(
                        "grantType",
                        String.valueOf(rs.getString("grant_type")),
                        "grantee",
                        String.valueOf(rs.getString("grantee_username")),
                        "canQuery",
                        rs.getBoolean("can_query"),
                        "canPreview",
                        rs.getBoolean("can_preview"),
                        "sourceRequestId",
                        String.valueOf(rs.getObject("source_request_id"))
                    ),
                    rs.getString("created_by"),
                    instant(rs, "created_date")
                );
            },
            datasetId
        );
    }

    private TrashView trash(UUID datasetId) {
        return jdbcTemplate
            .query(
                """
                select id, status, retain_until, seal_id, seal_version,
                       effective_level, restored_at, destroyed_at
                  from catalog_lifecycle_trash_fact
                 where dataset_id=?
                """,
                (rs, rowNum) ->
                    new TrashView(
                        rs.getObject("id", UUID.class),
                        rs.getString("status"),
                        instant(rs, "retain_until"),
                        rs.getObject("seal_id", UUID.class),
                        rs.getLong("seal_version"),
                        rs.getString("effective_level"),
                        instant(rs, "restored_at"),
                        instant(rs, "destroyed_at")
                    ),
                datasetId
            )
            .stream()
            .findFirst()
            .orElse(null);
    }

    private List<DestructionProofView> destructionProofs(UUID datasetId) {
        return jdbcTemplate.query(
            """
            select id, action_id, attempt_no, adapter_code, object_manifest::text,
                   manifest_checksum, first_approved_by, second_approved_by,
                   executed_by, result_status, external_source_touched,
                   failure_message, executed_at
              from catalog_destruction_proof
             where dataset_id=?
             order by executed_at, attempt_no
            """,
            (rs, rowNum) ->
                new DestructionProofView(
                    rs.getObject("id", UUID.class),
                    rs.getObject("action_id", UUID.class),
                    rs.getInt("attempt_no"),
                    rs.getString("adapter_code"),
                    parseMap(rs.getString("object_manifest")),
                    rs.getString("manifest_checksum"),
                    rs.getString("first_approved_by"),
                    rs.getString("second_approved_by"),
                    rs.getString("executed_by"),
                    rs.getString("result_status"),
                    rs.getBoolean("external_source_touched"),
                    rs.getString("failure_message"),
                    instant(rs, "executed_at")
                ),
            datasetId
        );
    }

    private String defaultStatus(String stage, CatalogDataset dataset) {
        return switch (stage) {
            case "CREATE" -> "REGISTERED";
            case "STORAGE" -> Boolean.TRUE.equals(dataset.getEnabled()) ? "ACTIVE" : "INACTIVE";
            case "ARCHIVE" -> "ARCHIVED".equalsIgnoreCase(dataset.getLifecycleStatus()) ? "ARCHIVED" : "NOT_STARTED";
            case "DESTROY" -> dataset.getLifecycleStatus() == null ? "NOT_STARTED" : dataset.getLifecycleStatus();
            default -> "NOT_STARTED";
        };
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toInstant();
    }

    private Map<String, Object> parseMap(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception failure) {
            return Map.of("unparsedEvidence", value);
        }
    }

    private void put(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    public record StageView(
        String stage,
        String status,
        String requestSource,
        String requestRef,
        String actor,
        Instant occurredAt,
        Map<String, Object> evidence
    ) {}

    public record EventView(
        String eventId,
        String stage,
        String eventType,
        String status,
        String requestSource,
        String requestRef,
        UUID sealId,
        Long sealVersion,
        String effectiveLevel,
        Long dataVolume,
        Map<String, Object> evidence,
        String actor,
        Instant occurredAt
    ) {}

    public record TrashView(
        UUID id,
        String status,
        Instant retainUntil,
        UUID sealId,
        long sealVersion,
        String effectiveLevel,
        Instant restoredAt,
        Instant destroyedAt
    ) {}

    public record DestructionProofView(
        UUID id,
        UUID actionId,
        int attemptNo,
        String adapterCode,
        Map<String, Object> objectManifest,
        String manifestChecksum,
        String firstApprovedBy,
        String secondApprovedBy,
        String executedBy,
        String resultStatus,
        boolean externalSourceTouched,
        String failureMessage,
        Instant executedAt
    ) {}

    public record LifecycleView(
        UUID datasetId,
        String datasetName,
        String ownerDept,
        String lifecycleStatus,
        boolean enabled,
        UUID sealId,
        long sealVersion,
        String effectiveLevel,
        List<StageView> stages,
        List<EventView> events,
        TrashView trash,
        List<DestructionProofView> destructionProofs
    ) {}
}
