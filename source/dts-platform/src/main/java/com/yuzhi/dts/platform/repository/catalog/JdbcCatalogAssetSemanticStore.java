package com.yuzhi.dts.platform.repository.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticStore;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.*;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL implementation of the bounded asset-semantics projection store. */
@Repository
public class JdbcCatalogAssetSemanticStore implements CatalogAssetSemanticStore {

    private static final String PROJECTION_NAME = "GLOBAL";
    private static final String UNASSIGNED_DOMAIN = "__UNASSIGNED__";
    private static final String UNLAYERED = "UNLAYERED";
    private static final Duration STALE_AFTER = Duration.ofMinutes(10);
    private static final int MAX_STATS_BUCKETS = 5_000;
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcCatalogAssetSemanticStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public RegistrationReceipt register(RegistrationPlan plan, Instant now) {
        lock(plan.assetType(), plan.assetKey());
        Optional<BucketKey> previous = findBucket(plan.assetType(), plan.assetKey());
        boolean evidenceCreated = !evidenceExists(plan.evidence());

        ProjectionWriteResult projection = upsertProjection(plan, now);
        upsertProducer(plan, now);
        upsertEvidence(plan.evidence(), now);

        BucketKey next = BucketKey.from(plan);
        if (projection.created()) {
            addToBucket(next, 1, now, projection.version());
        } else if (previous.isPresent() && !previous.orElseThrow().equals(next)) {
            addToBucket(previous.orElseThrow(), -1, now, projection.version());
            addToBucket(next, 1, now, projection.version());
        }
        markProjectionFresh(now);
        appendProcessedEvent(plan, projection.created() ? "ASSET_REGISTERED" : "ASSET_REFRESHED", now);
        return new RegistrationReceipt(projection.created(), evidenceCreated, projection.version(), now);
    }

    @Override
    public Optional<ProjectionMutationReceipt> updateGovernance(
        CatalogAssetType assetType,
        String assetKey,
        UUID domainId,
        GovernanceReadiness governance,
        Instant now
    ) {
        lock(assetType, assetKey);
        Optional<BucketKey> previous = findBucket(assetType, assetKey);
        if (previous.isEmpty()) {
            return Optional.empty();
        }
        ProjectionRow row = findProjection(assetType, assetKey).orElseThrow();
        StatusAxes axes = new StatusAxes(row.discovery(), governance, row.publication(), row.serving(), row.lifecycle());
        ConsumptionEligibility eligibility = CatalogAssetSemanticsContract.evaluateEligibility(
            axes,
            row.qualityGatePassed(),
            row.permissionGatePassed(),
            now
        );
        Long version = jdbc.queryForObject(
            """
            UPDATE catalog_asset_semantic_projection
               SET domain_id = ?, governance_readiness = ?, eligibility_decision = ?,
                   eligibility_reason_codes = ?::jsonb, eligibility_evaluated_at = ?,
                   projection_version = projection_version + 1, updated_at = ?
             WHERE asset_type = ? AND asset_key = ?
             RETURNING projection_version
            """,
            Long.class,
            domainId,
            governance.name(),
            eligibility.decision().name(),
            writeJson(eligibility.reasonCodes()),
            Timestamp.from(eligibility.evaluatedAt()),
            Timestamp.from(now),
            assetType.name(),
            assetKey
        );
        long projectionVersion = version == null ? row.version() + 1 : version;
        BucketKey next = new BucketKey(domainId, layerKey(row.canonicalLayer()), assetType, governance);
        if (!previous.orElseThrow().equals(next)) {
            addToBucket(previous.orElseThrow(), -1, now, projectionVersion);
            addToBucket(next, 1, now, projectionVersion);
        }
        markProjectionFresh(now);
        appendProcessedEvent(assetType, assetKey, "ASSET_GOVERNANCE_UPDATED", now);
        return Optional.of(new ProjectionMutationReceipt(projectionVersion, now));
    }

    @Override
    public Optional<AssetSemanticsView> find(CatalogAssetType assetType, String assetKey, Instant now) {
        Optional<ProjectionRow> projection = findProjection(assetType, assetKey);
        if (projection.isEmpty()) {
            return Optional.empty();
        }
        ProjectionRow row = projection.orElseThrow();
        ProducerRef producer = currentProducer(assetType, assetKey).orElse(null);
        List<RegistrationEvidence> evidence = evidence(assetType, assetKey);
        StatusAxes axes = new StatusAxes(row.discovery(), row.governance(), row.publication(), row.serving(), row.lifecycle());
        ConsumptionEligibility eligibility = new ConsumptionEligibility(
            row.eligibilityDecision(),
            readReasonCodes(row.eligibilityReasons()),
            row.eligibilityEvaluatedAt()
        );
        return Optional.of(
            new AssetSemanticsView(
                row.assetType(),
                row.assetKey(),
                row.resourceId(),
                row.relationType(),
                row.domainId(),
                row.canonicalLayer(),
                row.legacyLayerCode(),
                row.assetRole(),
                producer,
                evidence,
                axes,
                row.qualityGatePassed(),
                row.permissionGatePassed(),
                eligibility,
                row.version(),
                row.updatedAt()
            )
        );
    }

    @Override
    public StatsSnapshot stats(UUID domainId, Instant now) {
        ProjectionMeta meta = projectionMeta();
        String sql = """
            SELECT domain_id, warehouse_layer_key, asset_type, governance_readiness, asset_count
              FROM catalog_asset_stats_projection
             WHERE (?::uuid IS NULL OR domain_id = ?::uuid)
               AND asset_count > 0
             ORDER BY domain_key, warehouse_layer_key, asset_type, governance_readiness
             LIMIT ?
            """;
        List<StatsBucket> buckets = jdbc.query(
            sql,
            (rs, rowNum) -> new StatsBucket(
                uuid(rs, "domain_id"),
                nullableLayer(rs.getString("warehouse_layer_key")),
                CatalogAssetType.valueOf(rs.getString("asset_type")),
                GovernanceReadiness.valueOf(rs.getString("governance_readiness")),
                rs.getLong("asset_count")
            ),
            domainId,
            domainId,
            MAX_STATS_BUCKETS
        );
        long total = buckets.stream().mapToLong(StatsBucket::count).sum();
        Freshness freshness = freshness(meta, now);
        boolean approximate = freshness != Freshness.FRESH || buckets.size() == MAX_STATS_BUCKETS;
        return new StatsSnapshot(buckets, total, meta.asOf(), freshness, approximate, meta.state());
    }

    @Override
    public ReconciliationReceipt reconcile(Instant now) {
        jdbc.update(
            "UPDATE catalog_asset_stats_projection_meta SET state = 'REBUILDING', last_error = NULL WHERE projection_name = ?",
            PROJECTION_NAME
        );
        jdbc.update("DELETE FROM catalog_asset_stats_projection");
        jdbc.update(
            """
            INSERT INTO catalog_asset_stats_projection(
                domain_key, domain_id, warehouse_layer_key, asset_type, governance_readiness,
                asset_count, as_of, projection_version
            )
            SELECT coalesce(domain_id::text, ?), domain_id, coalesce(canonical_layer, ?), asset_type,
                   governance_readiness, count(*), ?, max(projection_version)
              FROM catalog_asset_semantic_projection
             GROUP BY domain_id, canonical_layer, asset_type, governance_readiness
            """,
            UNASSIGNED_DOMAIN,
            UNLAYERED,
            Timestamp.from(now)
        );
        Long assetCount = jdbc.queryForObject("SELECT count(*) FROM catalog_asset_semantic_projection", Long.class);
        Integer bucketCount = jdbc.queryForObject("SELECT count(*) FROM catalog_asset_stats_projection", Integer.class);
        jdbc.update(
            """
            UPDATE catalog_asset_stats_projection_meta
               SET state = 'FRESH', as_of = ?, last_reconciled_at = ?,
                   projection_version = projection_version + 1, last_error = NULL
             WHERE projection_name = ?
            """,
            Timestamp.from(now),
            Timestamp.from(now),
            PROJECTION_NAME
        );
        return new ReconciliationReceipt(assetCount == null ? 0 : assetCount, bucketCount == null ? 0 : bucketCount, now);
    }

    private void lock(CatalogAssetType assetType, String assetKey) {
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(? || ':' || ?, 0))")) {
                statement.setString(1, assetType.name());
                statement.setString(2, assetKey);
                statement.execute();
            }
            return null;
        });
    }

    private ProjectionWriteResult upsertProjection(RegistrationPlan plan, Instant now) {
        String sql = """
            INSERT INTO catalog_asset_semantic_projection(
                asset_type, asset_key, resource_id, relation_type, domain_id, canonical_layer,
                legacy_layer_code, asset_role, discovery_state, governance_readiness, publication_state,
                serving_health, lifecycle_state, quality_gate_passed, permission_gate_passed,
                eligibility_decision, eligibility_reason_codes, eligibility_evaluated_at,
                projection_version, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, 1, ?, ?)
            ON CONFLICT (asset_type, asset_key) DO UPDATE SET
                resource_id = coalesce(EXCLUDED.resource_id, catalog_asset_semantic_projection.resource_id),
                relation_type = EXCLUDED.relation_type,
                domain_id = EXCLUDED.domain_id,
                canonical_layer = EXCLUDED.canonical_layer,
                legacy_layer_code = EXCLUDED.legacy_layer_code,
                asset_role = EXCLUDED.asset_role,
                discovery_state = EXCLUDED.discovery_state,
                governance_readiness = EXCLUDED.governance_readiness,
                publication_state = EXCLUDED.publication_state,
                serving_health = EXCLUDED.serving_health,
                lifecycle_state = EXCLUDED.lifecycle_state,
                quality_gate_passed = EXCLUDED.quality_gate_passed,
                permission_gate_passed = EXCLUDED.permission_gate_passed,
                eligibility_decision = EXCLUDED.eligibility_decision,
                eligibility_reason_codes = EXCLUDED.eligibility_reason_codes,
                eligibility_evaluated_at = EXCLUDED.eligibility_evaluated_at,
                projection_version = catalog_asset_semantic_projection.projection_version + 1,
                updated_at = EXCLUDED.updated_at
            RETURNING (xmax = 0) AS created, projection_version
            """;
        StatusAxes axes = plan.statusAxes();
        ConsumptionEligibility eligibility = plan.eligibility();
        return jdbc.queryForObject(
            sql,
            (rs, rowNum) -> new ProjectionWriteResult(rs.getBoolean("created"), rs.getLong("projection_version")),
            plan.assetType().name(),
            plan.assetKey(),
            plan.resourceId(),
            plan.relationType().name(),
            plan.domainId(),
            plan.canonicalLayer(),
            plan.legacyLayerCode(),
            plan.assetRole().name(),
            axes.discovery().name(),
            axes.governance().name(),
            axes.publication().name(),
            axes.serving().name(),
            axes.lifecycle().name(),
            plan.qualityGatePassed(),
            plan.permissionGatePassed(),
            eligibility.decision().name(),
            writeJson(eligibility.reasonCodes()),
            Timestamp.from(eligibility.evaluatedAt()),
            Timestamp.from(now),
            Timestamp.from(now)
        );
    }

    private void upsertProducer(RegistrationPlan plan, Instant now) {
        Optional<ProducerRef> current = currentProducer(plan.assetType(), plan.assetKey());
        ProducerRef next = plan.producer();
        if (
            current.isPresent() &&
            current.orElseThrow().producerKind() == next.producerKind() &&
            current.orElseThrow().producerId().equals(next.producerId()) &&
            java.util.Objects.equals(current.orElseThrow().producerVersion(), next.producerVersion())
        ) {
            return;
        }
        jdbc.update(
            """
            UPDATE catalog_asset_producer_ref
               SET valid_to = ?
             WHERE asset_type = ? AND asset_key = ? AND valid_to IS NULL
            """,
            Timestamp.from(next.validFrom()),
            plan.assetType().name(),
            plan.assetKey()
        );
        jdbc.update(
            """
            INSERT INTO catalog_asset_producer_ref(
                id, asset_type, asset_key, producer_kind, producer_id, producer_version,
                valid_from, valid_to, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?)
            """,
            UUID.randomUUID(),
            plan.assetType().name(),
            plan.assetKey(),
            next.producerKind().name(),
            next.producerId(),
            next.producerVersion(),
            Timestamp.from(next.validFrom()),
            Timestamp.from(now)
        );
    }

    private void upsertEvidence(RegistrationEvidence evidence, Instant now) {
        jdbc.update(
            """
            INSERT INTO catalog_asset_registration_evidence(
                id, asset_type, asset_key, channel, evidence_ref, first_observed_at,
                last_observed_at, status, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (asset_type, asset_key, channel, evidence_ref) DO UPDATE SET
                last_observed_at = greatest(
                    catalog_asset_registration_evidence.last_observed_at,
                    EXCLUDED.last_observed_at
                ),
                status = EXCLUDED.status,
                updated_at = EXCLUDED.updated_at
            """,
            UUID.randomUUID(),
            evidence.assetType().name(),
            evidence.assetKey(),
            evidence.channel().name(),
            evidence.evidenceRef(),
            Timestamp.from(evidence.firstObservedAt()),
            Timestamp.from(evidence.lastObservedAt()),
            evidence.status().name(),
            Timestamp.from(now),
            Timestamp.from(now)
        );
    }

    private void addToBucket(BucketKey bucket, long delta, Instant now, long version) {
        jdbc.update(
            """
            INSERT INTO catalog_asset_stats_projection(
                domain_key, domain_id, warehouse_layer_key, asset_type, governance_readiness,
                asset_count, as_of, projection_version
            ) VALUES (?, ?, ?, ?, ?, greatest(0, ?), ?, ?)
            ON CONFLICT (domain_key, warehouse_layer_key, asset_type, governance_readiness) DO UPDATE SET
                asset_count = greatest(0, catalog_asset_stats_projection.asset_count + ?),
                as_of = EXCLUDED.as_of,
                projection_version = greatest(catalog_asset_stats_projection.projection_version, EXCLUDED.projection_version)
            """,
            bucket.domainKey(),
            bucket.domainId(),
            bucket.layerKey(),
            bucket.assetType().name(),
            bucket.governance().name(),
            delta,
            Timestamp.from(now),
            version,
            delta
        );
        jdbc.update("DELETE FROM catalog_asset_stats_projection WHERE asset_count = 0");
    }

    private void markProjectionFresh(Instant now) {
        jdbc.update(
            """
            UPDATE catalog_asset_stats_projection_meta
               SET state = 'FRESH', as_of = ?, projection_version = projection_version + 1, last_error = NULL
             WHERE projection_name = ?
            """,
            Timestamp.from(now),
            PROJECTION_NAME
        );
    }

    private void appendProcessedEvent(RegistrationPlan plan, String eventType, Instant now) {
        appendProcessedEvent(plan.assetType(), plan.assetKey(), eventType, now);
    }

    private void appendProcessedEvent(CatalogAssetType assetType, String assetKey, String eventType, Instant now) {
        jdbc.update(
            """
            INSERT INTO catalog_asset_projection_event(
                id, asset_type, asset_key, event_type, status, created_at, processed_at, attempts
            ) VALUES (?, ?, ?, ?, 'PROCESSED', ?, ?, 1)
            """,
            UUID.randomUUID(),
            assetType.name(),
            assetKey,
            eventType,
            Timestamp.from(now),
            Timestamp.from(now)
        );
    }

    private Optional<ProjectionRow> findProjection(CatalogAssetType assetType, String assetKey) {
        try {
            return Optional.ofNullable(
                jdbc.queryForObject(
                    "SELECT * FROM catalog_asset_semantic_projection WHERE asset_type = ? AND asset_key = ?",
                    this::mapProjection,
                    assetType.name(),
                    assetKey
                )
            );
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    private Optional<BucketKey> findBucket(CatalogAssetType assetType, String assetKey) {
        try {
            return Optional.ofNullable(
                jdbc.queryForObject(
                    """
                    SELECT domain_id, canonical_layer, asset_type, governance_readiness
                      FROM catalog_asset_semantic_projection
                     WHERE asset_type = ? AND asset_key = ?
                     FOR UPDATE
                    """,
                    (rs, rowNum) -> new BucketKey(
                        uuid(rs, "domain_id"),
                        layerKey(rs.getString("canonical_layer")),
                        CatalogAssetType.valueOf(rs.getString("asset_type")),
                        GovernanceReadiness.valueOf(rs.getString("governance_readiness"))
                    ),
                    assetType.name(),
                    assetKey
                )
            );
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    private Optional<ProducerRef> currentProducer(CatalogAssetType assetType, String assetKey) {
        try {
            return Optional.ofNullable(
                jdbc.queryForObject(
                    """
                    SELECT producer_kind, producer_id, producer_version, valid_from, valid_to
                      FROM catalog_asset_producer_ref
                     WHERE asset_type = ? AND asset_key = ? AND valid_to IS NULL
                    """,
                    (rs, rowNum) -> new ProducerRef(
                        ProducerKind.valueOf(rs.getString("producer_kind")),
                        rs.getString("producer_id"),
                        rs.getString("producer_version"),
                        instant(rs, "valid_from"),
                        instant(rs, "valid_to")
                    ),
                    assetType.name(),
                    assetKey
                )
            );
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    private List<RegistrationEvidence> evidence(CatalogAssetType assetType, String assetKey) {
        return jdbc.query(
            """
            SELECT channel, evidence_ref, first_observed_at, last_observed_at, status
              FROM catalog_asset_registration_evidence
             WHERE asset_type = ? AND asset_key = ?
             ORDER BY last_observed_at DESC, channel, evidence_ref
             LIMIT 200
            """,
            (rs, rowNum) -> new RegistrationEvidence(
                assetType,
                assetKey,
                EvidenceChannel.valueOf(rs.getString("channel")),
                rs.getString("evidence_ref"),
                instant(rs, "first_observed_at"),
                instant(rs, "last_observed_at"),
                EvidenceStatus.valueOf(rs.getString("status"))
            ),
            assetType.name(),
            assetKey
        );
    }

    private boolean evidenceExists(RegistrationEvidence evidence) {
        Integer count = jdbc.queryForObject(
            """
            SELECT count(*) FROM catalog_asset_registration_evidence
             WHERE asset_type = ? AND asset_key = ? AND channel = ? AND evidence_ref = ?
            """,
            Integer.class,
            evidence.assetType().name(),
            evidence.assetKey(),
            evidence.channel().name(),
            evidence.evidenceRef()
        );
        return count != null && count > 0;
    }

    private ProjectionMeta projectionMeta() {
        try {
            return jdbc.queryForObject(
                """
                SELECT state, as_of, last_reconciled_at, projection_version
                  FROM catalog_asset_stats_projection_meta
                 WHERE projection_name = ?
                """,
                (rs, rowNum) -> new ProjectionMeta(
                    rs.getString("state"),
                    instant(rs, "as_of"),
                    instant(rs, "last_reconciled_at"),
                    rs.getLong("projection_version")
                ),
                PROJECTION_NAME
            );
        } catch (EmptyResultDataAccessException ignored) {
            return new ProjectionMeta("REBUILDING", null, null, 0);
        }
    }

    private Freshness freshness(ProjectionMeta meta, Instant now) {
        if ("REBUILDING".equals(meta.state()) || meta.asOf() == null) {
            return Freshness.REBUILDING;
        }
        return meta.asOf().plus(STALE_AFTER).isBefore(now) ? Freshness.STALE : Freshness.FRESH;
    }

    private ProjectionRow mapProjection(ResultSet rs, int rowNum) throws SQLException {
        return new ProjectionRow(
            CatalogAssetType.valueOf(rs.getString("asset_type")),
            rs.getString("asset_key"),
            uuid(rs, "resource_id"),
            RelationType.valueOf(rs.getString("relation_type")),
            uuid(rs, "domain_id"),
            rs.getString("canonical_layer"),
            rs.getString("legacy_layer_code"),
            AssetRole.valueOf(rs.getString("asset_role")),
            DiscoveryState.valueOf(rs.getString("discovery_state")),
            GovernanceReadiness.valueOf(rs.getString("governance_readiness")),
            PublicationState.valueOf(rs.getString("publication_state")),
            ServingHealth.valueOf(rs.getString("serving_health")),
            LifecycleState.valueOf(rs.getString("lifecycle_state")),
            nullableBoolean(rs, "quality_gate_passed"),
            nullableBoolean(rs, "permission_gate_passed"),
            EligibilityDecision.valueOf(rs.getString("eligibility_decision")),
            rs.getString("eligibility_reason_codes"),
            instant(rs, "eligibility_evaluated_at"),
            rs.getLong("projection_version"),
            instant(rs, "updated_at")
        );
    }

    private String writeJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("cannot serialize eligibility reason codes", error);
        }
    }

    private List<String> readReasonCodes(String json) {
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("cannot deserialize eligibility reason codes", error);
        }
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }

    private static String layerKey(String canonicalLayer) {
        return canonicalLayer == null || canonicalLayer.isBlank() ? UNLAYERED : canonicalLayer;
    }

    private static String nullableLayer(String layerKey) {
        return UNLAYERED.equals(layerKey) ? null : layerKey;
    }

    private record ProjectionWriteResult(boolean created, long version) {}

    private record BucketKey(
        UUID domainId,
        String layerKey,
        CatalogAssetType assetType,
        GovernanceReadiness governance
    ) {
        static BucketKey from(RegistrationPlan plan) {
            return new BucketKey(
                plan.domainId(),
                JdbcCatalogAssetSemanticStore.layerKey(plan.canonicalLayer()),
                plan.assetType(),
                plan.statusAxes().governance()
            );
        }

        String domainKey() {
            return domainId == null ? UNASSIGNED_DOMAIN : domainId.toString();
        }
    }

    private record ProjectionMeta(String state, Instant asOf, Instant lastReconciledAt, long version) {}

    private record ProjectionRow(
        CatalogAssetType assetType,
        String assetKey,
        UUID resourceId,
        RelationType relationType,
        UUID domainId,
        String canonicalLayer,
        String legacyLayerCode,
        AssetRole assetRole,
        DiscoveryState discovery,
        GovernanceReadiness governance,
        PublicationState publication,
        ServingHealth serving,
        LifecycleState lifecycle,
        Boolean qualityGatePassed,
        Boolean permissionGatePassed,
        EligibilityDecision eligibilityDecision,
        String eligibilityReasons,
        Instant eligibilityEvaluatedAt,
        long version,
        Instant updatedAt
    ) {}
}
