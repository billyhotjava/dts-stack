package com.yuzhi.dts.platform.service.modeling.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.platform.service.modeling.migration.LegacyObjectMigrationPlanner.Classification;
import com.yuzhi.dts.platform.service.modeling.migration.LegacyObjectMigrationPlanner.LegacyModel;
import com.yuzhi.dts.platform.service.modeling.migration.LegacyObjectMigrationPlanner.LegacyObjectDecision;
import com.yuzhi.dts.platform.service.modeling.migration.LegacyObjectMigrationPlanner.LegacyObjectSnapshot;
import com.yuzhi.dts.platform.service.modeling.migration.LegacyObjectMigrationPlanner.LegacySource;
import com.yuzhi.dts.platform.service.modeling.migration.LegacyObjectMigrationPlanner.Report;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Read-only inventory, decision ledger, compatibility projection and physical-retirement gate. */
@Service
public class LegacyObjectMigrationService {

    public static final String SEMANTIC_SOURCE = "SEMANTIC";
    public static final String MODELING_VNEXT_SOURCE = "MODELING_VNEXT";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final LegacyObjectMigrationPlanner planner;
    private final String defaultTenantId;
    private final boolean writeFrozen;
    private final boolean legacyReadEnabled;
    private final boolean backupApproved;

    public LegacyObjectMigrationService(
        JdbcTemplate jdbc,
        ObjectMapper objectMapper,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String defaultTenantId,
        @Value("${dts.platform.modeling.legacy-object-write-frozen:true}") boolean writeFrozen,
        @Value("${dts.platform.modeling.legacy-object-read-enabled:true}") boolean legacyReadEnabled,
        @Value("${dts.platform.modeling.legacy-object-backup-approved:false}") boolean backupApproved
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.planner = new LegacyObjectMigrationPlanner();
        this.defaultTenantId = requireText(defaultTenantId, "default tenant id");
        this.writeFrozen = writeFrozen;
        this.legacyReadEnabled = legacyReadEnabled;
        this.backupApproved = backupApproved;
    }

    public boolean writeFrozen() {
        return writeFrozen;
    }

    public boolean legacyReadEnabled() {
        return legacyReadEnabled;
    }

    public String defaultTenantId() {
        return defaultTenantId;
    }

    /** A dry-run is deliberately side-effect free; its batch ID is derived from the stable source checksum. */
    @Transactional(readOnly = true)
    public Report dryRun(String tenantId) {
        String tenant = requireTenant(tenantId);
        Map<String, Long> counts = inventoryCounts();
        Map<String, Long> orphans = orphanCounts();
        Map<UUID, UUID> plansByDomain = targetPlansByDomain(tenant);
        List<LegacyObjectSnapshot> snapshots = new ArrayList<>();
        snapshots.addAll(semanticSnapshots(plansByDomain));
        snapshots.addAll(modelingVNextSnapshots(tenant));
        return planner.plan(counts, orphans, snapshots);
    }

    /**
     * Persists one idempotent, tenant-scoped decision batch. Unsafe/manual objects are isolated, never guessed.
     * A READY_FOR_APPROVAL row is evidence for a later explicit target migration, not an implicit write.
     */
    @Transactional
    public MigrationExecution execute(String tenantId, String batchId, String expectedChecksum, String actorId) {
        String tenant = requireTenant(tenantId);
        Report report = dryRun(tenant);
        if (!Objects.equals(report.batchId(), requireText(batchId, "batchId"))) {
            throw new IllegalStateException("MIGRATION_BATCH_STALE: dry-run batch identity changed");
        }
        if (!Objects.equals(report.checksum(), requireText(expectedChecksum, "expectedChecksum"))) {
            throw new IllegalStateException("MIGRATION_CHECKSUM_MISMATCH: dry-run checksum changed");
        }
        String actor = hasText(actorId) ? actorId.trim() : "system";
        boolean replayed = jdbc.queryForObject(
            "SELECT count(*) FROM modeling_legacy_object_migration_batch WHERE batch_id = ? AND tenant_id = ?",
            Long.class,
            report.batchId(),
            tenant
        ) > 0;

        long archived = report.decisions().stream().filter(item -> item.classification() == Classification.ARCHIVE_ONLY).count();
        long readyForApproval = report
            .decisions()
            .stream()
            .filter(item -> item.ready() && item.classification() != Classification.ARCHIVE_ONLY)
            .count();
        long isolated = report.decisions().size() - archived - readyForApproval;
        Map<String, Long> targetCounts = new LinkedHashMap<>();
        targetCounts.put("migrated", 0L);
        targetCounts.put("archivedReadonly", archived);
        targetCounts.put("isolated", isolated);
        targetCounts.put("readyForApproval", readyForApproval);
        targetCounts.put("accounted", (long) report.decisions().size());
        targetCounts.put("unaccounted", 0L);
        String status = readyForApproval > 0 ? "READY_FOR_APPROVAL" : "COMPLETED_ACCOUNTED";
        Timestamp now = Timestamp.from(Instant.now());

        jdbc.update(
            """
            INSERT INTO modeling_legacy_object_migration_batch
                (batch_id, tenant_id, source_checksum, status, source_counts, target_counts, orphan_report,
                 started_by, created_date, last_modified_date)
            VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?)
            ON CONFLICT (batch_id, tenant_id) DO UPDATE SET
                source_checksum = EXCLUDED.source_checksum,
                status = EXCLUDED.status,
                source_counts = EXCLUDED.source_counts,
                target_counts = EXCLUDED.target_counts,
                orphan_report = EXCLUDED.orphan_report,
                started_by = EXCLUDED.started_by,
                last_modified_date = EXCLUDED.last_modified_date
            """,
            report.batchId(),
            tenant,
            report.checksum(),
            status,
            json(report.sourceCounts()),
            json(targetCounts),
            json(report.orphanCounts()),
            actor,
            now,
            now
        );

        for (LegacyObjectDecision decision : report.decisions()) {
            String decisionStatus = decision.classification() == Classification.ARCHIVE_ONLY
                ? "ARCHIVED_READONLY"
                : decision.ready() ? "READY_FOR_APPROVAL" : "ISOLATED";
            jdbc.update(
                """
                INSERT INTO modeling_legacy_object_migration
                    (batch_id, tenant_id, legacy_source, legacy_id, legacy_code, classification, decision_status,
                     catalog_domain_id, target_plan_id, target_model_spec_id, target_type, target_ref,
                     source_checksum, details, created_date, last_modified_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, NULL, ?, ?::jsonb, ?, ?)
                ON CONFLICT (batch_id, tenant_id, legacy_source, legacy_id) DO UPDATE SET
                    legacy_code = EXCLUDED.legacy_code,
                    classification = EXCLUDED.classification,
                    decision_status = EXCLUDED.decision_status,
                    catalog_domain_id = EXCLUDED.catalog_domain_id,
                    target_plan_id = EXCLUDED.target_plan_id,
                    target_type = EXCLUDED.target_type,
                    source_checksum = EXCLUDED.source_checksum,
                    details = EXCLUDED.details,
                    last_modified_date = EXCLUDED.last_modified_date
                """,
                report.batchId(),
                tenant,
                decision.legacySource(),
                decision.legacyId(),
                decision.legacyCode(),
                decision.classification().name(),
                decisionStatus,
                decision.catalogDomainId(),
                decision.targetPlanId(),
                decision.targetType(),
                report.checksum(),
                json(decision),
                now,
                now
            );
        }
        return new MigrationExecution(report.batchId(), report.checksum(), status, replayed, Map.copyOf(targetCounts));
    }

    @Transactional(readOnly = true)
    public List<JsonNode> projectLegacyRead(String tenantId, String legacySource, Collection<?> values) {
        String tenant = requireAnyTenant(tenantId);
        List<?> safeValues = values == null ? List.of() : List.copyOf(values);
        Map<String, MigrationMapping> mappings = latestMappings(tenant, legacySource);
        List<JsonNode> result = new ArrayList<>();
        for (Object value : safeValues) {
            JsonNode raw = objectMapper.valueToTree(value);
            ObjectNode node = raw instanceof ObjectNode object ? object.deepCopy() : objectMapper.createObjectNode().set("legacyValue", raw);
            String id = node.path("id").asText("");
            MigrationMapping mapping = mappings.get(id);
            node.put("retirementStatus", "LEGACY_READONLY");
            ObjectNode migration = node.putObject("migration");
            migration.put("decisionStatus", mapping == null ? "NOT_EVALUATED" : mapping.decisionStatus());
            migration.put("classification", mapping == null ? "NEEDS_DRY_RUN" : mapping.classification());
            if (mapping != null && hasText(mapping.targetRef())) migration.put("targetRef", mapping.targetRef());
            migration.put("repairPath", "/api/modeling/migrations/legacy-objects/dry-run");
            result.add(node);
        }
        return List.copyOf(result);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordApiUsage(String tenantId, String callerId, String route, String httpMethod, String result) {
        String tenant = requireAnyTenant(tenantId);
        jdbc.update(
            """
            INSERT INTO modeling_legacy_api_usage
                (id, tenant_id, caller_id, route, http_method, result, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            tenant,
            hasText(callerId) ? callerId.trim() : null,
            requireText(route, "route"),
            requireText(httpMethod, "httpMethod").toUpperCase(Locale.ROOT),
            requireText(result, "result"),
            Timestamp.from(Instant.now())
        );
    }

    @Transactional(readOnly = true)
    public UsageReport usageReport(String tenantId, int days) {
        String tenant = requireTenant(tenantId);
        int observationDays = Math.min(Math.max(days, 1), 365);
        List<UsageCount> rows = jdbc.query(
            """
            SELECT route, http_method, result, count(*) AS calls, max(occurred_at) AS last_seen
              FROM modeling_legacy_api_usage
             WHERE tenant_id = ? AND occurred_at >= current_timestamp - (? * interval '1 day')
             GROUP BY route, http_method, result
             ORDER BY calls DESC, route, http_method
            """,
            (rs, rowNum) -> new UsageCount(
                rs.getString("route"),
                rs.getString("http_method"),
                rs.getString("result"),
                rs.getLong("calls"),
                rs.getTimestamp("last_seen").toInstant()
            ),
            tenant,
            observationDays
        );
        long calls = rows.stream().mapToLong(UsageCount::calls).sum();
        return new UsageReport(observationDays, calls, rows);
    }

    @Transactional(readOnly = true)
    public ExitGateView exitGate(String tenantId, int observationDays) {
        String tenant = requireTenant(tenantId);
        int days = Math.min(Math.max(observationDays, 1), 365);
        List<Map<String, Object>> batches = jdbc.queryForList(
            """
            SELECT batch_id, source_checksum, status, last_modified_date
              FROM modeling_legacy_object_migration_batch
             WHERE tenant_id = ?
             ORDER BY last_modified_date DESC
             LIMIT 1
            """,
            tenant
        );
        List<String> blockers = new ArrayList<>();
        String batchId = null;
        String checksum = null;
        long unresolved = 0;
        if (batches.isEmpty()) {
            blockers.add("MIGRATION_BATCH_REQUIRED");
        } else {
            Map<String, Object> batch = batches.get(0);
            batchId = text(batch.get("batch_id"));
            checksum = text(batch.get("source_checksum"));
            unresolved = jdbc.queryForObject(
                """
                SELECT count(*) FROM modeling_legacy_object_migration
                 WHERE tenant_id = ? AND batch_id = ? AND decision_status <> 'MIGRATED'
                """,
                Long.class,
                tenant,
                batchId
            );
            if (unresolved > 0) blockers.add("LEGACY_RECORDS_STILL_READONLY");
        }
        long recentCalls = jdbc.queryForObject(
            """
            SELECT count(*) FROM modeling_legacy_api_usage
             WHERE tenant_id = ? AND occurred_at >= current_timestamp - (? * interval '1 day')
            """,
            Long.class,
            tenant,
            days
        );
        if (recentCalls > 0) blockers.add("LEGACY_CONSUMERS_NOT_ZERO");
        if (!backupApproved) blockers.add("BACKUP_APPROVAL_REQUIRED");
        return new ExitGateView(
            "NO-DROP",
            batchId,
            checksum,
            days,
            recentCalls,
            unresolved,
            backupApproved,
            List.copyOf(blockers)
        );
    }

    private Map<String, Long> inventoryCounts() {
        return queryNamedCounts(
            """
            SELECT 'catalogDomain' AS name, count(*) AS value FROM catalog_domain
            UNION ALL SELECT 'semanticSubjectDomain', count(*) FROM semantic_subject_domain
            UNION ALL SELECT 'semanticBusinessObject', count(*) FROM semantic_business_object
            UNION ALL SELECT 'semanticModel', count(*) FROM semantic_model
            UNION ALL SELECT 'semanticTableMapping', count(*) FROM semantic_object_table_mapping
            UNION ALL SELECT 'semanticDimension', count(*) FROM semantic_dimension
            UNION ALL SELECT 'semanticMetric', count(*) FROM semantic_metric
            UNION ALL SELECT 'semanticArtifact', count(*) FROM semantic_generated_artifact
            UNION ALL SELECT 'semanticRun', count(*) FROM semantic_model_run
            UNION ALL SELECT 'semanticReview', count(*) FROM semantic_model_review_log
            UNION ALL SELECT 'modelingBusinessObject', count(*) FROM modeling_business_object
            UNION ALL SELECT 'modelingModelSpec', count(*) FROM modeling_model_spec
            """
        );
    }

    private Map<String, Long> orphanCounts() {
        return queryNamedCounts(
            """
            SELECT 'objectWithoutSubjectDomain' AS name, count(*) AS value
              FROM semantic_business_object o LEFT JOIN semantic_subject_domain d ON d.id = o.domain_id WHERE d.id IS NULL
            UNION ALL SELECT 'mappingWithoutObject', count(*)
              FROM semantic_object_table_mapping x LEFT JOIN semantic_business_object o ON o.id = x.object_id WHERE o.id IS NULL
            UNION ALL SELECT 'dimensionWithoutObject', count(*)
              FROM semantic_dimension x LEFT JOIN semantic_business_object o ON o.id = x.object_id WHERE o.id IS NULL
            UNION ALL SELECT 'metricWithoutObject', count(*)
              FROM semantic_metric x LEFT JOIN semantic_business_object o ON o.id = x.object_id WHERE o.id IS NULL
            UNION ALL SELECT 'modelWithoutObject', count(*)
              FROM semantic_model x LEFT JOIN semantic_business_object o ON o.id = x.object_id WHERE o.id IS NULL
            UNION ALL SELECT 'artifactWithoutModel', count(*)
              FROM semantic_generated_artifact x LEFT JOIN semantic_model m ON m.id = x.model_id WHERE m.id IS NULL
            UNION ALL SELECT 'runWithoutModel', count(*)
              FROM semantic_model_run x LEFT JOIN semantic_model m ON m.id = x.model_id WHERE m.id IS NULL
            UNION ALL SELECT 'reviewWithoutModel', count(*)
              FROM semantic_model_review_log x LEFT JOIN semantic_model m ON m.id = x.model_id WHERE m.id IS NULL
            UNION ALL SELECT 'legacyModelSpecWithoutObject', count(*)
              FROM modeling_model_spec x LEFT JOIN modeling_business_object o ON o.id = x.object_id
             WHERE x.contract_version = 1 AND o.id IS NULL
            """
        );
    }

    private Map<String, Long> queryNamedCounts(String sql) {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.queryForList(sql).forEach(row -> result.put(text(row.get("name")), ((Number) row.get("value")).longValue()));
        return Map.copyOf(result);
    }

    private Map<UUID, UUID> targetPlansByDomain(String tenantId) {
        return jdbc
            .query(
                """
                SELECT DISTINCT ON (domain_id) domain_id, id
                  FROM modeling_warehouse_plan
                 WHERE tenant_id = ? AND domain_id IS NOT NULL
                   AND COALESCE(lifecycle_status, status, 'DRAFT') <> 'ARCHIVED'
                 ORDER BY domain_id, business_scope_confirmed DESC NULLS LAST, last_modified_date DESC NULLS LAST, id
                """,
                (rs, rowNum) -> Map.entry(UUID.fromString(rs.getString("domain_id")), UUID.fromString(rs.getString("id"))),
                tenantId
            )
            .stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private List<LegacyObjectSnapshot> semanticSnapshots(Map<UUID, UUID> plansByDomain) {
        Map<UUID, List<LegacyModel>> models = groupSemanticModels();
        Map<UUID, List<LegacySource>> sources = groupSemanticSources();
        return jdbc.query(
            """
            SELECT o.id, o.code, o.name, o.domain_id, o.process_id, o.status, o.primary_key,
                   d.governance_domain_id AS catalog_domain_id,
                   (SELECT count(*) FROM semantic_dimension x WHERE x.object_id = o.id) AS dimensions,
                   (SELECT count(*) FROM semantic_metric x WHERE x.object_id = o.id) AS metrics,
                   (SELECT count(*) FROM semantic_generated_artifact a JOIN semantic_model m ON m.id = a.model_id WHERE m.object_id = o.id) AS artifacts,
                   (SELECT count(*) FROM semantic_model_run r JOIN semantic_model m ON m.id = r.model_id WHERE m.object_id = o.id) AS runs,
                   (SELECT count(*) FROM semantic_model_review_log r JOIN semantic_model m ON m.id = r.model_id WHERE m.object_id = o.id) AS reviews
              FROM semantic_business_object o
              LEFT JOIN semantic_subject_domain d ON d.id = o.domain_id
             ORDER BY o.id
            """,
            (rs, rowNum) -> {
                UUID id = UUID.fromString(rs.getString("id"));
                UUID legacyDomain = uuid(rs.getObject("domain_id"));
                UUID catalogDomain = uuid(rs.getObject("catalog_domain_id"));
                return new LegacyObjectSnapshot(
                    SEMANTIC_SOURCE,
                    id,
                    rs.getString("code"),
                    rs.getString("name"),
                    null,
                    legacyDomain,
                    catalogDomain,
                    catalogDomain == null ? null : plansByDomain.get(catalogDomain),
                    rs.getString("process_id"),
                    rs.getString("status"),
                    splitKeys(rs.getString("primary_key")),
                    models.getOrDefault(id, List.of()),
                    sources.getOrDefault(id, List.of()),
                    rs.getInt("dimensions"),
                    rs.getInt("metrics"),
                    rs.getInt("artifacts"),
                    rs.getInt("runs"),
                    rs.getInt("reviews")
                );
            }
        );
    }

    private Map<UUID, List<LegacyModel>> groupSemanticModels() {
        return groupByObject(
            jdbc.query(
                "SELECT id, object_id, type, grain FROM semantic_model ORDER BY object_id, id",
                (rs, rowNum) -> new ObjectValue<>(
                    UUID.fromString(rs.getString("object_id")),
                    new LegacyModel(rs.getString("id"), rs.getString("type"), splitKeys(rs.getString("grain")))
                )
            )
        );
    }

    private Map<UUID, List<LegacySource>> groupSemanticSources() {
        return groupByObject(
            jdbc.query(
                """
                SELECT id, object_id, table_name, table_role, join_expression, sort_order
                  FROM semantic_object_table_mapping ORDER BY object_id, sort_order, id
                """,
                (rs, rowNum) -> new ObjectValue<>(
                    UUID.fromString(rs.getString("object_id")),
                    new LegacySource(
                        rs.getString("id"),
                        rs.getString("table_name"),
                        rs.getString("table_role"),
                        rs.getString("join_expression"),
                        rs.getInt("sort_order")
                    )
                )
            )
        );
    }

    private List<LegacyObjectSnapshot> modelingVNextSnapshots(String tenantId) {
        return jdbc.query(
            """
            SELECT o.id, o.code, o.name, o.object_kind, o.process_id, o.status, o.business_key, o.source_refs,
                   p.id AS target_plan_id, p.domain_id AS catalog_domain_id,
                   (SELECT count(*) FROM modeling_model_spec s WHERE s.tenant_id = o.tenant_id AND s.object_id = o.id) AS models
              FROM modeling_business_object o
              LEFT JOIN LATERAL (
                  SELECT candidate.id, candidate.domain_id
                    FROM modeling_warehouse_plan candidate
                   WHERE candidate.tenant_id = o.tenant_id
                     AND candidate.process_id = o.process_id
                     AND candidate.domain_id IS NOT NULL
                     AND COALESCE(candidate.lifecycle_status, candidate.status, 'DRAFT') <> 'ARCHIVED'
                   ORDER BY candidate.business_scope_confirmed DESC NULLS LAST,
                            candidate.last_modified_date DESC NULLS LAST, candidate.id
                   LIMIT 1
              ) p ON true
             WHERE o.tenant_id = ?
             ORDER BY o.id
            """,
            (rs, rowNum) -> {
                UUID id = UUID.fromString(rs.getString("id"));
                UUID domain = uuid(rs.getObject("catalog_domain_id"));
                return new LegacyObjectSnapshot(
                    MODELING_VNEXT_SOURCE,
                    id,
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getString("object_kind"),
                    domain,
                    domain,
                    uuid(rs.getObject("target_plan_id")),
                    rs.getString("process_id"),
                    rs.getString("status"),
                    parseStringArray(rs.getString("business_key")),
                    modelingModels(tenantId, id),
                    parseVNextSources(rs.getString("source_refs")),
                    "DIMENSION".equalsIgnoreCase(rs.getString("object_kind")) ? 1 : 0,
                    0,
                    0,
                    0,
                    0
                );
            },
            tenantId
        );
    }

    private List<LegacyModel> modelingModels(String tenantId, UUID objectId) {
        return jdbc.query(
            """
            SELECT id, model_type, grain_statement FROM modeling_model_spec
             WHERE tenant_id = ? AND object_id = ? ORDER BY id
            """,
            (rs, rowNum) -> new LegacyModel(rs.getString("id"), rs.getString("model_type"), splitKeys(rs.getString("grain_statement"))),
            tenantId,
            objectId
        );
    }

    private List<LegacySource> parseVNextSources(String value) {
        if (!hasText(value)) return List.of();
        try {
            JsonNode root = objectMapper.readTree(value);
            if (!root.isArray()) return List.of();
            List<LegacySource> result = new ArrayList<>();
            int index = 0;
            for (JsonNode item : root) {
                result.add(new LegacySource(
                    "source-" + index,
                    item.path("ref").asText(null),
                    item.path("kind").asText("LEGACY_SOURCE"),
                    null,
                    index
                ));
                index++;
            }
            return List.copyOf(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("LEGACY_SOURCE_REFS_INVALID", exception);
        }
    }

    private List<String> parseStringArray(String value) {
        if (!hasText(value)) return List.of();
        try {
            JsonNode root = objectMapper.readTree(value);
            if (root.isArray()) {
                List<String> values = new ArrayList<>();
                root.forEach(item -> {
                    if (item.isTextual() && hasText(item.asText())) values.add(item.asText().trim());
                });
                return List.copyOf(values);
            }
        } catch (JsonProcessingException ignored) {
            // Older rows may use comma-separated text; parse it below without losing the record.
        }
        return splitKeys(value);
    }

    private Map<String, MigrationMapping> latestMappings(String tenantId, String legacySource) {
        return jdbc
            .query(
                """
                SELECT DISTINCT ON (legacy_id) legacy_id, classification, decision_status, target_ref
                  FROM modeling_legacy_object_migration
                 WHERE tenant_id = ? AND legacy_source = ?
                 ORDER BY legacy_id, last_modified_date DESC, batch_id DESC
                """,
                (rs, rowNum) -> new MigrationMapping(
                    rs.getString("legacy_id"),
                    rs.getString("classification"),
                    rs.getString("decision_status"),
                    rs.getString("target_ref")
                ),
                tenantId,
                requireText(legacySource, "legacySource")
            )
            .stream()
            .collect(Collectors.toUnmodifiableMap(MigrationMapping::legacyId, Function.identity()));
    }

    private String requireTenant(String tenantId) {
        String tenant = requireAnyTenant(tenantId);
        if (!defaultTenantId.equals(tenant)) {
            throw new IllegalArgumentException("TENANT_SCOPE_FORBIDDEN: legacy semantic rows belong to the server tenant");
        }
        return tenant;
    }

    private static String requireAnyTenant(String tenantId) {
        return requireText(tenantId, "tenantId");
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("MIGRATION_REPORT_SERIALIZATION_FAILED", exception);
        }
    }

    private static <T> Map<UUID, List<T>> groupByObject(List<ObjectValue<T>> values) {
        return values.stream().collect(Collectors.groupingBy(ObjectValue::objectId, LinkedHashMap::new, Collectors.mapping(ObjectValue::value, Collectors.toList())));
    }

    private static List<String> splitKeys(String value) {
        if (!hasText(value)) return List.of();
        return java.util.Arrays
            .stream(value.split("[,，\\s]+"))
            .map(String::trim)
            .filter(LegacyObjectMigrationService::hasText)
            .distinct()
            .toList();
    }

    private static UUID uuid(Object value) {
        if (value == null) return null;
        return value instanceof UUID id ? id : UUID.fromString(String.valueOf(value));
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String requireText(String value, String field) {
        if (!hasText(value)) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private record ObjectValue<T>(UUID objectId, T value) {}

    private record MigrationMapping(String legacyId, String classification, String decisionStatus, String targetRef) {}

    public record MigrationExecution(
        String batchId,
        String checksum,
        String status,
        boolean replayed,
        Map<String, Long> reconciliation
    ) {}

    public record UsageCount(String route, String httpMethod, String result, long calls, Instant lastSeen) {}

    public record UsageReport(int observationDays, long calls, List<UsageCount> routes) {}

    public record ExitGateView(
        String decision,
        String batchId,
        String checksum,
        int observationDays,
        long recentLegacyCalls,
        long unresolvedRecords,
        boolean backupApproved,
        List<String> blockers
    ) {}
}
