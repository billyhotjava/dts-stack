package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationEvent;
import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationPropagationService.ImpactExplanation;
import com.yuzhi.dts.platform.service.catalog.CatalogLifecycleControlService.ActionView;
import com.yuzhi.dts.platform.service.catalog.CatalogLifecycleProjectionService.LifecycleView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class CatalogGovernanceWorkbenchService {

    private final JdbcTemplate jdbcTemplate;
    private final CatalogClassificationService classificationService;
    private final CatalogLifecycleProjectionService lifecycleProjectionService;
    private final CatalogLifecycleControlService lifecycleControlService;
    private final CatalogClassificationPropagationService propagationService;

    public CatalogGovernanceWorkbenchService(
        JdbcTemplate jdbcTemplate,
        CatalogClassificationService classificationService,
        CatalogLifecycleProjectionService lifecycleProjectionService,
        CatalogLifecycleControlService lifecycleControlService,
        CatalogClassificationPropagationService propagationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.classificationService = classificationService;
        this.lifecycleProjectionService = lifecycleProjectionService;
        this.lifecycleControlService = lifecycleControlService;
        this.propagationService = propagationService;
    }

    public List<ClassificationFactView> classificationFacts(List<SubjectRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        if (requests.size() > 200) {
            throw new IllegalArgumentException("At most 200 classification subjects may be requested");
        }
        Map<String, SubjectRequest> distinct = new LinkedHashMap<>();
        for (SubjectRequest request : requests) {
            if (request == null || !StringUtils.hasText(request.subjectKey())) {
                continue;
            }
            String type = normalizeSubjectType(request.subjectType());
            String key = request.subjectKey().trim();
            distinct.putIfAbsent(type + "\u0000" + key, new SubjectRequest(type, key));
        }
        return distinct
            .values()
            .stream()
            .map(request -> classificationFact(request.subjectType(), request.subjectKey()))
            .toList();
    }

    public ClassificationFactView classificationFact(String subjectType, String subjectKey) {
        String type = normalizeSubjectType(subjectType);
        String key = required(subjectKey, "subjectKey");
        return classificationService
            .resolve(type, key)
            .map(snapshot -> toFact(snapshot, classificationService.explain(type, key).events()))
            .orElseGet(() -> ClassificationFactView.missing(type, key));
    }

    public AssetWorkspace workspace(UUID datasetId, String subjectKey, String activeDept) {
        if (datasetId == null) {
            throw new IllegalArgumentException("datasetId is required");
        }
        return new AssetWorkspace(
            classificationFact("ASSET", subjectKey),
            lifecycleProjectionService.project(datasetId, activeDept),
            lifecycleControlService.list(datasetId, null, 100),
            propagationService.explainImpact(datasetId)
        );
    }

    public LifecycleMetrics lifecycleMetrics(String ownerDept, String classification, int days) {
        String normalizedDept = trim(ownerDept);
        String normalizedLevel = trim(classification);
        if (normalizedLevel != null) {
            normalizedLevel = normalizedLevel.toUpperCase(Locale.ROOT);
        }
        int safeDays = Math.max(1, Math.min(days, 366));
        List<LifecycleMetricRow> current = jdbcTemplate.query(
            """
            with latest_volume as (
                select distinct on (dataset_id) dataset_id, data_volume
                  from catalog_lifecycle_event
                 where data_volume is not null
                 order by dataset_id, occurred_at desc, id desc
            ),
            buckets as (
                select 'IN_USE'::varchar as lifecycle_bucket,
                       coalesce(d.classification, 'INTERNAL')::varchar as effective_level,
                       d.owner_dept, v.data_volume
                  from catalog_dataset d
                  left join latest_volume v on v.dataset_id=d.id
                 where d.enabled=true
                   and coalesce(d.lifecycle_status, 'ACTIVE') not in ('ARCHIVED','TRASHED','DESTROYED')
                union all
                select 'SHARED', b.effective_level, null, null
                  from catalog_classification_access_binding b
                 where b.status='ACTIVE'
                union all
                select 'ARCHIVED', coalesce(d.classification, 'INTERNAL'), d.owner_dept, v.data_volume
                  from catalog_dataset d
                  left join latest_volume v on v.dataset_id=d.id
                 where d.lifecycle_status='ARCHIVED'
                union all
                select 'TRASH', t.effective_level, d.owner_dept, v.data_volume
                  from catalog_lifecycle_trash_fact t
                  join catalog_dataset d on d.id=t.dataset_id
                  left join latest_volume v on v.dataset_id=d.id
                 where t.status in ('TRASHED','DESTRUCTION_CANDIDATE','RESTORE_REQUESTED')
                union all
                select 'DESTROYED', a.effective_level, d.owner_dept, v.data_volume
                  from catalog_destruction_proof p
                  join catalog_lifecycle_control_action a on a.id=p.action_id
                  join catalog_dataset d on d.id=p.dataset_id
                  left join latest_volume v on v.dataset_id=d.id
                 where p.result_status='SUCCESS'
            )
            select lifecycle_bucket, effective_level, count(*) as asset_count,
                   sum(data_volume) filter (where data_volume is not null) as known_data_volume,
                   count(*) filter (where data_volume is null) as unknown_volume_count
              from buckets
             where (?::varchar is null or owner_dept=?)
               and (?::varchar is null or effective_level=?)
             group by lifecycle_bucket, effective_level
             order by lifecycle_bucket, effective_level
            """,
            (rs, rowNum) -> new LifecycleMetricRow(
                rs.getString("lifecycle_bucket"),
                rs.getString("effective_level"),
                rs.getLong("asset_count"),
                nullableLong(rs, "known_data_volume"),
                rs.getLong("unknown_volume_count")
            ),
            normalizedDept,
            normalizedDept,
            normalizedLevel,
            normalizedLevel
        );
        List<LifecycleTrendRow> trends = jdbcTemplate.query(
            """
            select date_trunc('day', e.occurred_at) as day,
                   e.stage, e.status, coalesce(e.effective_level, 'INTERNAL') as effective_level,
                   count(*) as event_count,
                   sum(e.data_volume) filter (where e.data_volume is not null) as known_data_volume,
                   count(*) filter (where e.data_volume is null) as unknown_volume_count
              from catalog_lifecycle_event e
              join catalog_dataset d on d.id=e.dataset_id
             where e.occurred_at >= current_timestamp - (? * interval '1 day')
               and (?::varchar is null or d.owner_dept=?)
               and (?::varchar is null or e.effective_level=?)
             group by date_trunc('day', e.occurred_at), e.stage, e.status, coalesce(e.effective_level, 'INTERNAL')
             order by day asc, e.stage, e.status, effective_level
            """,
            (rs, rowNum) -> new LifecycleTrendRow(
                instant(rs, "day"),
                rs.getString("stage"),
                rs.getString("status"),
                rs.getString("effective_level"),
                rs.getLong("event_count"),
                nullableLong(rs, "known_data_volume"),
                rs.getLong("unknown_volume_count")
            ),
            safeDays,
            normalizedDept,
            normalizedDept,
            normalizedLevel,
            normalizedLevel
        );
        return new LifecycleMetrics(current, trends, safeDays, normalizedDept, normalizedLevel);
    }

    public List<GovernanceIssueView> governanceIssues(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return jdbcTemplate.query(
            """
            select issue_type, issue_status, subject_ref, error_message, occurred_at,
                   responsible_owner, responsible_dept, dataset_id
              from (
                    select 'LINEAGE_PROPAGATION'::varchar as issue_type, j.status as issue_status,
                           j.target_asset_key as subject_ref, j.last_error as error_message,
                           coalesce(j.last_modified_date, j.created_date) as occurred_at,
                           d.owner as responsible_owner, d.owner_dept as responsible_dept,
                           j.target_dataset_id as dataset_id
                      from catalog_classification_propagation_job j
                      join catalog_dataset d on d.id=j.target_dataset_id
                     where j.status in ('BLOCKED','FAILED','RETRY')
                    union all
                    select 'CONSUMER_RECOMPUTE', status, consumer_type || ':' || consumer_key,
                           last_error, coalesce(last_modified_date, created_date),
                           null, null, null
                      from catalog_classification_consumer_job
                     where status in ('BLOCKED','FAILED','RETRY','PENDING')
                    union all
                    select 'CACHE_INVALIDATION', status, aggregate_key, last_error,
                           coalesce(last_modified_date, created_date),
                           null, null, null
                      from catalog_classification_invalidation_outbox
                     where status in ('FAILED','RETRY','PENDING')
              ) issues
             order by occurred_at desc nulls last
             limit ?
            """,
            (rs, rowNum) -> new GovernanceIssueView(
                rs.getString("issue_type"),
                rs.getString("issue_status"),
                redact(rs.getString("subject_ref")),
                rs.getString("error_message"),
                instant(rs, "occurred_at"),
                rs.getString("responsible_owner"),
                rs.getString("responsible_dept"),
                repairRoute(rs.getString("issue_type"), rs.getObject("dataset_id", UUID.class))
            ),
            safeLimit
        );
    }

    private ClassificationFactView toFact(
        CatalogClassificationSnapshot snapshot,
        List<CatalogClassificationEvent> events
    ) {
        List<ClassificationEventView> eventViews = events
            .stream()
            .limit(20)
            .map(event -> new ClassificationEventView(
                event.getEventType(),
                event.getPreviousLevel(),
                event.getCandidateLevel(),
                event.getResultingLevel(),
                event.getTriggerType(),
                redact(event.getTriggerRef()),
                event.getOccurredAt(),
                event.getSnapshotVersion()
            ))
            .toList();
        CatalogClassificationEvent highest = events
            .stream()
            .filter(event -> Objects.equals(snapshot.getEffectiveLevel(), event.getResultingLevel()))
            .findFirst()
            .orElse(null);
        return new ClassificationFactView(
            snapshot.getSubjectType(),
            snapshot.getSubjectKey(),
            true,
            snapshot.getDeclaredLevel(),
            snapshot.getDetectedLevel(),
            snapshot.getManualFloor(),
            snapshot.getEffectiveLevel(),
            snapshot.getOriginType(),
            redact(snapshot.getOriginRef()),
            highest == null ? snapshot.getOriginType() : highest.getTriggerType(),
            highest == null ? redact(snapshot.getOriginRef()) : redact(highest.getTriggerRef()),
            snapshot.getPropagationStatus(),
            snapshot.getRecordVersion() == null ? 0L : snapshot.getRecordVersion(),
            snapshot.getId(),
            snapshot.getSealedAt(),
            eventViews
        );
    }

    private static String normalizeSubjectType(String subjectType) {
        return StringUtils.hasText(subjectType) ? subjectType.trim().toUpperCase(Locale.ROOT) : "ASSET";
    }

    private static String required(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String redact(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder("ref:");
            for (int i = 0; i < 6; i++) {
                out.append(String.format("%02x", hash[i]));
            }
            return out.toString();
        } catch (Exception exception) {
            return "ref:redacted";
        }
    }

    private static String repairRoute(String issueType, UUID datasetId) {
        return "LINEAGE_PROPAGATION".equals(issueType) && datasetId != null
            ? "/catalog/datasets/" + datasetId + "?tab=lineage-impact"
            : "/catalog/assets?view=table";
    }

    public record SubjectRequest(String subjectType, String subjectKey) {}

    public record ClassificationEventView(
        String eventType,
        String previousLevel,
        String candidateLevel,
        String resultingLevel,
        String triggerType,
        String triggerRef,
        Instant occurredAt,
        long snapshotVersion
    ) {}

    public record ClassificationFactView(
        String subjectType,
        String subjectKey,
        boolean sealed,
        String declaredLevel,
        String detectedLevel,
        String manualFloor,
        String effectiveLevel,
        String originType,
        String originRef,
        String highestSourceType,
        String highestSourceRef,
        String propagationStatus,
        long snapshotVersion,
        UUID snapshotId,
        Instant sealedAt,
        List<ClassificationEventView> events
    ) {
        static ClassificationFactView missing(String subjectType, String subjectKey) {
            return new ClassificationFactView(
                subjectType,
                subjectKey,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "UNSEALED",
                0L,
                null,
                null,
                List.of()
            );
        }
    }

    public record AssetWorkspace(
        ClassificationFactView classification,
        LifecycleView lifecycle,
        List<ActionView> actions,
        ImpactExplanation classificationImpact
    ) {}

    public record LifecycleMetricRow(
        String lifecycleBucket,
        String effectiveLevel,
        long assetCount,
        Long knownDataVolume,
        long unknownVolumeCount
    ) {}

    public record LifecycleTrendRow(
        Instant day,
        String stage,
        String status,
        String effectiveLevel,
        long eventCount,
        Long knownDataVolume,
        long unknownVolumeCount
    ) {}

    public record LifecycleMetrics(
        List<LifecycleMetricRow> current,
        List<LifecycleTrendRow> trends,
        int days,
        String ownerDept,
        String classification
    ) {}

    public record GovernanceIssueView(
        String issueType,
        String status,
        String redactedSubjectRef,
        String errorMessage,
        Instant occurredAt,
        String responsibleOwner,
        String responsibleDept,
        String repairRoute
    ) {}
}
