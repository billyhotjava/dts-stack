package com.yuzhi.dts.platform.service.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

@Service
@Transactional
public class CatalogConsumerClassificationService {

    private static final List<String> CONSUMER_TYPES = List.of(
        "METRIC",
        "CARD",
        "REPORT",
        "API",
        "DATA_PRODUCT",
        "REPORT_LINK",
        "SCREEN",
        "EXPORT"
    );

    private final JdbcTemplate jdbcTemplate;
    private final CatalogClassificationService classificationService;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    public CatalogConsumerClassificationService(
        JdbcTemplate jdbcTemplate,
        CatalogClassificationService classificationService,
        ObjectMapper objectMapper,
        ObjectProvider<CacheManager> cacheManager
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.classificationService = classificationService;
        this.objectMapper = objectMapper;
        this.cacheManager = cacheManager.getIfAvailable();
    }

    public DerivationResult derive(DeriveCommand command) {
        Objects.requireNonNull(command, "command");
        String consumerType = consumerType(command.consumerType());
        String consumerKey = required(command.consumerKey(), "consumerKey", 512);
        List<SubjectRef> upstreams = normalizeUpstreams(command.upstreams());
        String manualFloor = command.manualFloor() == null || command.manualFloor().isBlank()
            ? null
            : SecurityLevelCatalog.requireDataLevel(command.manualFloor()).code();
        if (upstreams.isEmpty() && manualFloor == null) {
            throw new CatalogClassificationException(
                "CONSUMER_CLASSIFICATION_SOURCE_REQUIRED",
                "Consumer classification requires at least one real upstream source or an explicit manual floor"
            );
        }

        List<ResolvedSource> resolved = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (SubjectRef upstream : upstreams) {
            CatalogClassificationSnapshot snapshot = classificationService
                .resolve(upstream.subjectType(), upstream.subjectKey())
                .orElse(null);
            if (
                snapshot == null ||
                !CatalogClassificationService.STATUS_PROPAGATED.equals(snapshot.getPropagationStatus())
            ) {
                missing.add(upstream.subjectType() + "/" + upstream.subjectKey());
                continue;
            }
            resolved.add(
                new ResolvedSource(
                    upstream.subjectType(),
                    upstream.subjectKey(),
                    snapshot.getId(),
                    version(snapshot),
                    snapshot.getEffectiveLevel()
                )
            );
        }
        if (!missing.isEmpty()) {
            throw new CatalogClassificationException(
                "CONSUMER_CLASSIFICATION_SOURCE_MISSING",
                "Consumer source classification is missing or pending: " + String.join(", ", missing)
            );
        }
        String upstreamMax = SecurityLevelCatalog.maxDataCode(
            resolved.stream().map(ResolvedSource::effectiveLevel).toList()
        );
        String effective = SecurityLevelCatalog.maxDataCode(upstreamMax, manualFloor);
        String evidenceJson = json(
            Map.of(
                "consumerType",
                consumerType,
                "consumerKey",
                consumerKey,
                "upstreams",
                resolved,
                "manualFloor",
                manualFloor == null ? "" : manualFloor
            )
        );
        CatalogClassificationSnapshot snapshot = classificationService.sealOrRaise(
            new CatalogClassificationService.SealCommand(
                "ASSET",
                consumerKey,
                consumerType,
                null,
                null,
                manualFloor,
                resolved.stream().map(ResolvedSource::effectiveLevel).toList(),
                "UPSTREAM_INHERITANCE",
                command.originRef(),
                sha256(consumerType + ":" + consumerKey + ":" + evidenceJson),
                evidenceJson
            )
        );
        replaceDependencies(consumerType, consumerKey, resolved);
        upsertState(consumerType, consumerKey, manualFloor, snapshot, evidenceJson);
        return new DerivationResult(
            consumerType,
            consumerKey,
            snapshot.getEffectiveLevel(),
            snapshot.getId(),
            version(snapshot),
            resolved,
            manualFloor,
            List.of()
        );
    }

    public ExportSeal sealExport(
        String fileSubjectKey,
        List<SubjectRef> upstreams,
        String originRef
    ) {
        String fileKey = required(fileSubjectKey, "fileSubjectKey", 512);
        DerivationResult derived = derive(
            new DeriveCommand("EXPORT", "export:" + fileKey, null, upstreams, originRef)
        );
        CatalogClassificationSnapshot fileSeal = classificationService.sealOrRaise(
            new CatalogClassificationService.SealCommand(
                "FILE",
                fileKey,
                "EXPORT",
                null,
                null,
                null,
                List.of(derived.effectiveLevel()),
                "UPSTREAM_INHERITANCE",
                originRef,
                sha256(fileKey + ":" + derived.snapshotId() + ":" + derived.snapshotVersion()),
                json(Map.of("derivedConsumerKey", derived.consumerKey()))
            )
        );
        return new ExportSeal(
            fileSeal.getId(),
            fileSeal.getSubjectKey(),
            fileSeal.getEffectiveLevel(),
            version(fileSeal)
        );
    }

    public AccessBindingView bindAccess(BindAccessCommand command, String actor) {
        Objects.requireNonNull(command, "command");
        String bindingType = required(command.bindingType(), "bindingType", 32).toUpperCase(Locale.ROOT);
        String bindingKey = required(command.bindingKey(), "bindingKey", 512);
        String consumerType = consumerType(command.consumerType());
        String consumerKey = required(command.consumerKey(), "consumerKey", 512);
        CatalogClassificationSnapshot snapshot = classificationService
            .resolve("ASSET", consumerKey)
            .orElseThrow(() ->
                new CatalogClassificationException(
                    "CONSUMER_CLASSIFICATION_NOT_FOUND",
                    "Consumer classification has not been derived"
                )
            );
        if (!CatalogClassificationService.STATUS_PROPAGATED.equals(snapshot.getPropagationStatus())) {
            throw new CatalogClassificationException(
                "CONSUMER_CLASSIFICATION_PENDING",
                "Consumer classification propagation is pending"
            );
        }
        Timestamp now = timestamp(Instant.now());
        Timestamp validTo = timestamp(command.validTo());
        jdbcTemplate.update(
            """
            insert into catalog_classification_access_binding (
                id, binding_type, binding_key, consumer_type, consumer_key,
                snapshot_id, snapshot_version, effective_level, status, valid_to,
                created_by, created_date, last_modified_by, last_modified_date
            ) values (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?)
            on conflict (binding_type, binding_key) do update
               set consumer_type=excluded.consumer_type,
                   consumer_key=excluded.consumer_key,
                   snapshot_id=excluded.snapshot_id,
                   snapshot_version=excluded.snapshot_version,
                   effective_level=excluded.effective_level,
                   status='ACTIVE', valid_to=excluded.valid_to,
                   suspended_reason=null,
                   last_modified_by=excluded.last_modified_by,
                   last_modified_date=excluded.last_modified_date
            """,
            UUID.randomUUID(),
            bindingType,
            bindingKey,
            consumerType,
            consumerKey,
            snapshot.getId(),
            version(snapshot),
            snapshot.getEffectiveLevel(),
            validTo,
            auditActor(actor),
            now,
            auditActor(actor),
            now
        );
        return accessBinding(bindingType, bindingKey);
    }

    public void revokeAccessBinding(String bindingType, String bindingKey, String actor) {
        int updated = jdbcTemplate.update(
            """
            update catalog_classification_access_binding
               set status='REVOKED',
                   suspended_reason='Access grant revoked',
                   last_modified_by=?,
                   last_modified_date=?
             where binding_type=? and binding_key=? and status <> 'REVOKED'
            """,
            auditActor(actor),
            timestamp(Instant.now()),
            required(bindingType, "bindingType", 32).toUpperCase(Locale.ROOT),
            required(bindingKey, "bindingKey", 512)
        );
        if (updated == 0) {
            return;
        }
        clearCaches();
    }

    @Transactional(readOnly = true)
    public AccessBindingView requireCurrentAccessBinding(String bindingType, String bindingKey) {
        AccessBindingView binding = accessBinding(
            required(bindingType, "bindingType", 32).toUpperCase(Locale.ROOT),
            required(bindingKey, "bindingKey", 512)
        );
        if (!"ACTIVE".equals(binding.status())) {
            throw new CatalogClassificationException(
                "CONSUMER_ACCESS_BINDING_SUSPENDED",
                "Consumer access binding is not active"
            );
        }
        if (binding.validTo() != null && Instant.now().isAfter(binding.validTo())) {
            throw new CatalogClassificationException(
                "CONSUMER_ACCESS_BINDING_EXPIRED",
                "Consumer access binding has expired"
            );
        }
        CatalogClassificationSnapshot current = classificationService
            .resolve("ASSET", binding.consumerKey())
            .orElseThrow(() ->
                new CatalogClassificationException(
                    "CONSUMER_CLASSIFICATION_NOT_FOUND",
                    "Consumer classification has not been derived"
                )
            );
        if (
            !current.getId().equals(binding.snapshotId()) ||
            version(current) != binding.snapshotVersion() ||
            !Objects.equals(current.getEffectiveLevel(), binding.effectiveLevel()) ||
            !CatalogClassificationService.STATUS_PROPAGATED.equals(current.getPropagationStatus())
        ) {
            throw new CatalogClassificationException(
                "CONSUMER_ACCESS_BINDING_STALE",
                "Consumer classification changed; approval and access binding must be renewed"
            );
        }
        return binding;
    }

    @Transactional(readOnly = true)
    public DerivationResult explain(String consumerType, String consumerKey) {
        String normalizedType = consumerType(consumerType);
        String normalizedKey = required(consumerKey, "consumerKey", 512);
        StateRow state = state(normalizedType, normalizedKey);
        List<ResolvedSource> sources = dependencies(normalizedType, normalizedKey);
        return new DerivationResult(
            normalizedType,
            normalizedKey,
            state.effectiveLevel(),
            state.snapshotId(),
            state.snapshotVersion(),
            sources,
            state.manualFloor(),
            state.lastError() == null ? List.of() : List.of(state.lastError())
        );
    }

    @Transactional(readOnly = true)
    public DerivationResult requireCurrentConsumer(String consumerType, String consumerKey) {
        String normalizedType = consumerType(consumerType);
        String normalizedKey = required(consumerKey, "consumerKey", 512);
        StateRow state = state(normalizedType, normalizedKey);
        if (!"CURRENT".equals(state.status())) {
            throw new CatalogClassificationException(
                "CONSUMER_CLASSIFICATION_RECOMPUTE_PENDING",
                "Consumer classification is not current"
            );
        }
        List<ResolvedSource> sources = dependencies(normalizedType, normalizedKey);
        for (ResolvedSource source : sources) {
            CatalogClassificationSnapshot current = classificationService
                .resolve(source.subjectType(), source.subjectKey())
                .orElseThrow(() ->
                    new CatalogClassificationException(
                        "CONSUMER_CLASSIFICATION_SOURCE_MISSING",
                        "Consumer upstream classification is missing"
                    )
                );
            if (
                !current.getId().equals(source.snapshotId()) ||
                version(current) != source.snapshotVersion() ||
                !Objects.equals(current.getEffectiveLevel(), source.effectiveLevel()) ||
                !CatalogClassificationService.STATUS_PROPAGATED.equals(current.getPropagationStatus())
            ) {
                throw new CatalogClassificationException(
                    "CONSUMER_CLASSIFICATION_STALE",
                    "Consumer upstream classification changed; recomputation is required"
                );
            }
        }
        return new DerivationResult(
            normalizedType,
            normalizedKey,
            state.effectiveLevel(),
            state.snapshotId(),
            state.snapshotVersion(),
            sources,
            state.manualFloor(),
            List.of()
        );
    }

    public void onUpstreamChanged(CatalogClassificationSnapshot upstream) {
        if (upstream == null || upstream.getSubjectType() == null || upstream.getSubjectKey() == null) {
            return;
        }
        List<ConsumerRef> consumers = jdbcTemplate.query(
            """
            select distinct consumer_type, consumer_key
              from catalog_classification_dependency
             where upstream_subject_type=? and upstream_subject_key=?
            """,
            (rs, rowNum) -> new ConsumerRef(rs.getString(1), rs.getString(2)),
            upstream.getSubjectType(),
            upstream.getSubjectKey()
        );
        for (ConsumerRef consumer : consumers) {
            suspendBindings(consumer, upstream);
            enqueueRecompute(consumer, upstream);
        }
        if (!consumers.isEmpty()) {
            clearCaches();
            appendInvalidation(
                upstream.getSubjectKey(),
                "UPSTREAM_CLASSIFICATION_CHANGED",
                Map.of(
                    "upstreamSubjectType",
                    upstream.getSubjectType(),
                    "upstreamSubjectKey",
                    upstream.getSubjectKey(),
                    "snapshotVersion",
                    version(upstream),
                    "effectiveLevel",
                    upstream.getEffectiveLevel(),
                    "affectedConsumers",
                    consumers
                )
            );
        }
    }

    @Scheduled(fixedDelayString = "${dts.catalog.classification.consumer-delay-ms:3000}")
    public void processRecomputeJobs() {
        List<JobRow> jobs = jdbcTemplate.query(
            """
            select id, consumer_type, consumer_key, attempts
              from catalog_classification_consumer_job
             where status in ('PENDING','RETRY') and next_attempt_at<=?
             order by created_date
             limit 50
             for update skip locked
            """,
            (rs, rowNum) ->
                new JobRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("consumer_type"),
                    rs.getString("consumer_key"),
                    rs.getInt("attempts")
                ),
            timestamp(Instant.now())
        );
        for (JobRow job : jobs) {
            processJob(job);
        }
    }

    private void processJob(JobRow job) {
        int attempt = job.attempts() + 1;
        Timestamp now = timestamp(Instant.now());
        jdbcTemplate.update(
            """
            update catalog_classification_consumer_job
               set status='RUNNING', attempts=?, last_modified_by='system', last_modified_date=?
             where id=?
            """,
            attempt,
            now,
            job.id()
        );
        try {
            StateRow state = state(job.consumerType(), job.consumerKey());
            List<SubjectRef> refs = dependencies(job.consumerType(), job.consumerKey())
                .stream()
                .map(source -> new SubjectRef(source.subjectType(), source.subjectKey()))
                .toList();
            derive(
                new DeriveCommand(
                    job.consumerType(),
                    job.consumerKey(),
                    state.manualFloor(),
                    refs,
                    "consumer-job:" + job.id()
                )
            );
            jdbcTemplate.update(
                """
                update catalog_classification_consumer_job
                   set status='DONE', next_attempt_at=null, last_error=null,
                       last_modified_by='system', last_modified_date=?
                 where id=?
                """,
                timestamp(Instant.now()),
                job.id()
            );
        } catch (RuntimeException failure) {
            boolean exhausted = attempt >= 5;
            jdbcTemplate.update(
                """
                update catalog_classification_consumer_job
                   set status=?, next_attempt_at=?, last_error=?,
                       last_modified_by='system', last_modified_date=?
                 where id=?
                """,
                exhausted ? "FAILED" : "RETRY",
                exhausted ? null : timestamp(Instant.now().plus(Duration.ofSeconds(1L << attempt))),
                truncate(failure.getMessage(), 2048),
                timestamp(Instant.now()),
                job.id()
            );
            jdbcTemplate.update(
                """
                update catalog_classification_consumer_state
                   set status=?, last_error=?, record_version=record_version+1,
                       last_modified_by='system', last_modified_date=?
                 where consumer_type=? and consumer_key=?
                """,
                exhausted ? "FAILED" : "RECOMPUTE_PENDING",
                truncate(failure.getMessage(), 2048),
                timestamp(Instant.now()),
                job.consumerType(),
                job.consumerKey()
            );
        }
    }

    private void replaceDependencies(
        String consumerType,
        String consumerKey,
        List<ResolvedSource> sources
    ) {
        jdbcTemplate.update(
            "delete from catalog_classification_dependency where consumer_type=? and consumer_key=?",
            consumerType,
            consumerKey
        );
        Timestamp now = timestamp(Instant.now());
        for (ResolvedSource source : sources) {
            jdbcTemplate.update(
                """
                insert into catalog_classification_dependency (
                    id, consumer_type, consumer_key, upstream_subject_type,
                    upstream_subject_key, upstream_snapshot_id,
                    upstream_snapshot_version, upstream_effective_level,
                    created_by, created_date
                ) values (?, ?, ?, ?, ?, ?, ?, ?, 'system', ?)
                """,
                UUID.randomUUID(),
                consumerType,
                consumerKey,
                source.subjectType(),
                source.subjectKey(),
                source.snapshotId(),
                source.snapshotVersion(),
                source.effectiveLevel(),
                now
            );
        }
    }

    private void upsertState(
        String consumerType,
        String consumerKey,
        String manualFloor,
        CatalogClassificationSnapshot snapshot,
        String evidenceJson
    ) {
        Timestamp now = timestamp(Instant.now());
        jdbcTemplate.update(
            """
            insert into catalog_classification_consumer_state (
                id, consumer_type, consumer_key, manual_floor, effective_level,
                status, snapshot_id, snapshot_version, evidence_checksum,
                record_version, created_by, created_date, last_modified_by, last_modified_date
            ) values (?, ?, ?, ?, ?, 'CURRENT', ?, ?, ?, 0, 'system', ?, 'system', ?)
            on conflict (consumer_type, consumer_key) do update
               set manual_floor=excluded.manual_floor,
                   effective_level=excluded.effective_level,
                   status='CURRENT', snapshot_id=excluded.snapshot_id,
                   snapshot_version=excluded.snapshot_version,
                   evidence_checksum=excluded.evidence_checksum,
                   last_error=null, record_version=catalog_classification_consumer_state.record_version+1,
                   last_modified_by='system', last_modified_date=excluded.last_modified_date
            """,
            UUID.randomUUID(),
            consumerType,
            consumerKey,
            manualFloor,
            snapshot.getEffectiveLevel(),
            snapshot.getId(),
            version(snapshot),
            sha256(evidenceJson),
            now,
            now
        );
    }

    private List<ResolvedSource> dependencies(String consumerType, String consumerKey) {
        return jdbcTemplate.query(
            """
            select upstream_subject_type, upstream_subject_key, upstream_snapshot_id,
                   upstream_snapshot_version, upstream_effective_level
              from catalog_classification_dependency
             where consumer_type=? and consumer_key=?
             order by upstream_subject_type, upstream_subject_key
            """,
            (rs, rowNum) ->
                new ResolvedSource(
                    rs.getString(1),
                    rs.getString(2),
                    rs.getObject(3, UUID.class),
                    rs.getLong(4),
                    rs.getString(5)
                ),
            consumerType,
            consumerKey
        );
    }

    private StateRow state(String consumerType, String consumerKey) {
        return jdbcTemplate
            .query(
                """
                select manual_floor, effective_level, snapshot_id, snapshot_version, status, last_error
                  from catalog_classification_consumer_state
                 where consumer_type=? and consumer_key=?
                """,
                (rs, rowNum) ->
                    new StateRow(
                        rs.getString("manual_floor"),
                        rs.getString("effective_level"),
                        rs.getObject("snapshot_id", UUID.class),
                        rs.getLong("snapshot_version"),
                        rs.getString("status"),
                        rs.getString("last_error")
                    ),
                consumerType,
                consumerKey
            )
            .stream()
            .findFirst()
            .orElseThrow(() ->
                new CatalogClassificationException(
                    "CONSUMER_CLASSIFICATION_NOT_FOUND",
                    "Consumer classification has not been derived"
                )
            );
    }

    private void enqueueRecompute(ConsumerRef consumer, CatalogClassificationSnapshot upstream) {
        String key = sha256(
            consumer.consumerType() +
            ":" +
            consumer.consumerKey() +
            ":" +
            upstream.getSubjectKey() +
            ":" +
            version(upstream)
        );
        Timestamp now = timestamp(Instant.now());
        jdbcTemplate.update(
            """
            insert into catalog_classification_consumer_job (
                id, idempotency_key, consumer_type, consumer_key, status,
                attempts, next_attempt_at, created_by, created_date,
                last_modified_by, last_modified_date
            ) values (?, ?, ?, ?, 'PENDING', 0, ?, 'system', ?, 'system', ?)
            on conflict (idempotency_key) do nothing
            """,
            UUID.randomUUID(),
            key,
            consumer.consumerType(),
            consumer.consumerKey(),
            now,
            now,
            now
        );
        jdbcTemplate.update(
            """
            update catalog_classification_consumer_state
               set status='RECOMPUTE_PENDING', record_version=record_version+1,
                   last_modified_by='system', last_modified_date=?
             where consumer_type=? and consumer_key=?
            """,
            now,
            consumer.consumerType(),
            consumer.consumerKey()
        );
    }

    private void suspendBindings(ConsumerRef consumer, CatalogClassificationSnapshot upstream) {
        Timestamp now = timestamp(Instant.now());
        jdbcTemplate.update(
            """
            update catalog_classification_access_binding
               set status='SUSPENDED', valid_to=least(coalesce(valid_to, ?), ?),
                   suspended_reason=?, last_modified_by='system', last_modified_date=?
             where consumer_type=? and consumer_key=? and status='ACTIVE'
            """,
            now,
            now,
            "Upstream classification changed: " + upstream.getSubjectKey(),
            now,
            consumer.consumerType(),
            consumer.consumerKey()
        );
    }

    private AccessBindingView accessBinding(String bindingType, String bindingKey) {
        return jdbcTemplate
            .query(
                """
                select id, binding_type, binding_key, consumer_type, consumer_key,
                       snapshot_id, snapshot_version, effective_level, status,
                       valid_to, suspended_reason, created_date
                  from catalog_classification_access_binding
                 where binding_type=? and binding_key=?
                """,
                (rs, rowNum) -> accessBinding(rs),
                bindingType,
                bindingKey
            )
            .stream()
            .findFirst()
            .orElseThrow(() ->
                new CatalogClassificationException(
                    "CONSUMER_ACCESS_BINDING_NOT_FOUND",
                    "Consumer access binding does not exist"
                )
            );
    }

    private AccessBindingView accessBinding(ResultSet rs) throws SQLException {
        return new AccessBindingView(
            rs.getObject("id", UUID.class),
            rs.getString("binding_type"),
            rs.getString("binding_key"),
            rs.getString("consumer_type"),
            rs.getString("consumer_key"),
            rs.getObject("snapshot_id", UUID.class),
            rs.getLong("snapshot_version"),
            rs.getString("effective_level"),
            rs.getString("status"),
            rs.getTimestamp("valid_to") == null ? null : rs.getTimestamp("valid_to").toInstant(),
            rs.getString("suspended_reason"),
            rs.getTimestamp("created_date").toInstant()
        );
    }

    private void clearCaches() {
        if (cacheManager == null) {
            return;
        }
        for (String name : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    private void appendInvalidation(String aggregateKey, String eventType, Map<String, Object> payload) {
        Timestamp now = timestamp(Instant.now());
        jdbcTemplate.update(
            """
            insert into catalog_classification_invalidation_outbox (
                id, aggregate_key, event_type, payload, status, attempts,
                next_attempt_at, created_by, created_date, last_modified_by, last_modified_date
            ) values (?, ?, ?, cast(? as jsonb), 'PENDING', 0, ?, 'system', ?, 'system', ?)
            """,
            UUID.randomUUID(),
            aggregateKey,
            eventType,
            json(payload),
            now,
            now,
            now
        );
    }

    private List<SubjectRef> normalizeUpstreams(List<SubjectRef> upstreams) {
        if (upstreams == null) {
            return List.of();
        }
        return upstreams
            .stream()
            .filter(Objects::nonNull)
            .map(ref ->
                new SubjectRef(
                    required(ref.subjectType(), "upstream.subjectType", 32).toUpperCase(Locale.ROOT),
                    required(ref.subjectKey(), "upstream.subjectKey", 512)
                )
            )
            .distinct()
            .sorted(Comparator.comparing(SubjectRef::subjectType).thenComparing(SubjectRef::subjectKey))
            .toList();
    }

    private String consumerType(String value) {
        String normalized = required(value, "consumerType", 32).toUpperCase(Locale.ROOT);
        if (!CONSUMER_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported consumer type: " + normalized);
        }
        return normalized;
    }

    private long version(CatalogClassificationSnapshot snapshot) {
        return snapshot.getRecordVersion() == null ? 0L : snapshot.getRecordVersion();
    }

    private String required(String value, String field, int max) {
        String normalized = value == null || value.trim().isEmpty() ? null : value.trim();
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (normalized.length() > max) {
            throw new IllegalArgumentException(field + " exceeds " + max + " characters");
        }
        return normalized;
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private String auditActor(String actor) {
        String normalized = actor == null || actor.trim().isEmpty() ? "system" : actor.trim();
        return normalized.length() <= 50 ? normalized : normalized.substring(0, 50);
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to serialize consumer classification evidence", failure);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to calculate consumer classification checksum", failure);
        }
    }

    private record ConsumerRef(String consumerType, String consumerKey) {}

    private record StateRow(
        String manualFloor,
        String effectiveLevel,
        UUID snapshotId,
        long snapshotVersion,
        String status,
        String lastError
    ) {}

    private record JobRow(UUID id, String consumerType, String consumerKey, int attempts) {}

    public record SubjectRef(String subjectType, String subjectKey) {}

    public record ResolvedSource(
        String subjectType,
        String subjectKey,
        UUID snapshotId,
        long snapshotVersion,
        String effectiveLevel
    ) {}

    public record DeriveCommand(
        String consumerType,
        String consumerKey,
        String manualFloor,
        List<SubjectRef> upstreams,
        String originRef
    ) {}

    public record DerivationResult(
        String consumerType,
        String consumerKey,
        String effectiveLevel,
        UUID snapshotId,
        long snapshotVersion,
        List<ResolvedSource> upstreams,
        String manualFloor,
        List<String> blockers
    ) {}

    public record ExportSeal(UUID sealId, String subjectKey, String effectiveLevel, long snapshotVersion) {}

    public record BindAccessCommand(
        String bindingType,
        String bindingKey,
        String consumerType,
        String consumerKey,
        Instant validTo
    ) {}

    public record AccessBindingView(
        UUID id,
        String bindingType,
        String bindingKey,
        String consumerType,
        String consumerKey,
        UUID snapshotId,
        long snapshotVersion,
        String effectiveLevel,
        String status,
        Instant validTo,
        String suspendedReason,
        Instant createdAt
    ) {}
}
