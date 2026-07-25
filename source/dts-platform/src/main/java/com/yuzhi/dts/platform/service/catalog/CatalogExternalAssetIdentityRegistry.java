package com.yuzhi.dts.platform.service.catalog;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CatalogExternalAssetIdentityRegistry {

    private static final int MAX_REGISTRATIONS = 500;
    private static final int LEGACY_UNKNOWN_BATCH_COUNT =
        Integer.MAX_VALUE;
    private static final Pattern METRIC_KEY = Pattern.compile(
        "^tenant:([^/]+)/env:prod/dialect:generic/metric-pack:([^/]+)/metric:([^/]+)$"
    );
    private static final Pattern METRIC_PACK_KEY = Pattern.compile(
        "^tenant:([^/]+)/env:prod/dialect:generic/metric-pack:([^/]+)/version:([^/]+)$"
    );
    private static final String UPSERT_SQL =
        """
        insert into catalog_external_asset_identity (
            id,
            asset_type,
            canonical_asset_key,
            remote_asset_id,
            source_service,
            registration_scope,
            sync_run_id,
            owner_dept,
            active,
            created_by,
            created_date,
            last_modified_by,
            last_modified_date
        ) values (?, ?, ?, ?, ?, ?, ?, ?, true, ?, ?, ?, ?)
        on conflict (asset_type, canonical_asset_key) do update
           set remote_asset_id = excluded.remote_asset_id,
               source_service = excluded.source_service,
               registration_scope = excluded.registration_scope,
               sync_run_id = excluded.sync_run_id,
               owner_dept = excluded.owner_dept,
               active = true,
               last_modified_by = excluded.last_modified_by,
               last_modified_date = excluded.last_modified_date
        """;
    private static final String STAGE_SQL =
        """
        insert into catalog_external_asset_sync_item (
            identity_id,
            source_service,
            registration_scope,
            sync_run_id,
            asset_type,
            canonical_asset_key,
            remote_asset_id,
            owner_dept,
            created_date,
            last_modified_date
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        on conflict (
            source_service,
            registration_scope,
            sync_run_id,
            asset_type,
            canonical_asset_key
        ) do update
           set remote_asset_id = excluded.remote_asset_id,
               owner_dept = excluded.owner_dept,
               last_modified_date = excluded.last_modified_date
        """;
    private static final String INSERT_SYNC_STATE_SQL =
        """
        insert into catalog_external_asset_sync_state (
            source_service,
            registration_scope,
            active_run_id,
            next_batch_index,
            batch_count,
            last_completed_run_id,
            last_modified_date
        ) values (?, ?, null, 0, null, null, ?)
        on conflict (source_service, registration_scope) do nothing
        """;
    private static final String SELECT_SYNC_STATE_SQL =
        """
        select active_run_id,
               next_batch_index,
               batch_count,
               last_completed_run_id
          from catalog_external_asset_sync_state
         where source_service = ?
           and registration_scope = ?
         for update
        """;
    private static final String CLAIM_SYNC_RUN_SQL =
        """
        update catalog_external_asset_sync_state
           set active_run_id = ?,
               next_batch_index = 0,
               batch_count = ?,
               last_modified_date = ?
         where source_service = ?
           and registration_scope = ?
        """;
    private static final String SELECT_SYNC_RUN_SQL =
        """
        select status,
               batch_count
          from catalog_external_asset_sync_run
         where source_service = ?
           and registration_scope = ?
           and sync_run_id = ?
         for update
        """;
    private static final String INSERT_SYNC_RUN_SQL =
        """
        insert into catalog_external_asset_sync_run (
            source_service,
            registration_scope,
            sync_run_id,
            status,
            batch_count,
            started_date,
            finished_date,
            last_modified_date
        ) values (?, ?, ?, 'ACTIVE', ?, ?, null, ?)
        """;
    private static final String SUPERSEDE_SYNC_RUN_SQL =
        """
        update catalog_external_asset_sync_run
           set status = 'SUPERSEDED',
               finished_date = ?,
               last_modified_date = ?
         where source_service = ?
           and registration_scope = ?
           and sync_run_id = ?
           and status = 'ACTIVE'
        """;
    private static final String COMPLETE_SYNC_RUN_RECORD_SQL =
        """
        update catalog_external_asset_sync_run
           set status = 'COMPLETED',
               finished_date = ?,
               last_modified_date = ?
         where source_service = ?
           and registration_scope = ?
           and sync_run_id = ?
           and status = 'ACTIVE'
        """;
    private static final String ADVANCE_SYNC_RUN_SQL =
        """
        update catalog_external_asset_sync_state
           set next_batch_index = ?,
               last_modified_date = ?
         where source_service = ?
           and registration_scope = ?
           and active_run_id = ?
           and next_batch_index = ?
           and batch_count = ?
        """;
    private static final String ADVANCE_LEGACY_SYNC_RUN_SQL =
        """
        update catalog_external_asset_sync_state
           set next_batch_index = next_batch_index + 1,
               last_modified_date = ?
         where source_service = ?
           and registration_scope = ?
           and active_run_id = ?
           and batch_count = ?
        """;
    private static final String COMPLETE_SYNC_RUN_SQL =
        """
        update catalog_external_asset_sync_state
           set active_run_id = null,
               next_batch_index = 0,
               batch_count = null,
               last_completed_run_id = ?,
               last_modified_date = ?
         where source_service = ?
           and registration_scope = ?
           and active_run_id = ?
           and next_batch_index = ?
           and batch_count = ?
        """;
    private static final String COMPLETE_LEGACY_SYNC_RUN_SQL =
        """
        update catalog_external_asset_sync_state
           set active_run_id = null,
               next_batch_index = 0,
               batch_count = null,
               last_completed_run_id = ?,
               last_modified_date = ?
         where source_service = ?
           and registration_scope = ?
           and active_run_id = ?
           and batch_count = ?
        """;
    private static final String MERGE_CURRENT_SQL =
        """
        insert into catalog_external_asset_identity (
            id,
            asset_type,
            canonical_asset_key,
            remote_asset_id,
            source_service,
            registration_scope,
            sync_run_id,
            owner_dept,
            active,
            created_by,
            created_date,
            last_modified_by,
            last_modified_date
        )
        select identity_id,
               asset_type,
               canonical_asset_key,
               remote_asset_id,
               source_service,
               registration_scope,
               sync_run_id,
               owner_dept,
               true,
               source_service,
               created_date,
               source_service,
               last_modified_date
          from catalog_external_asset_sync_item
         where source_service = ?
           and registration_scope = ?
           and sync_run_id = ?
        on conflict (asset_type, canonical_asset_key) do update
           set remote_asset_id = excluded.remote_asset_id,
               source_service = excluded.source_service,
               registration_scope = excluded.registration_scope,
               sync_run_id = excluded.sync_run_id,
               owner_dept = excluded.owner_dept,
               active = true,
               last_modified_by = excluded.last_modified_by,
               last_modified_date = excluded.last_modified_date
        returning asset_type,
                  canonical_asset_key,
                  remote_asset_id,
                  owner_dept
        """;
    private static final String DELETE_STAGED_ITEMS_SQL =
        """
        delete from catalog_external_asset_sync_item
         where source_service = ?
           and registration_scope = ?
           and sync_run_id = ?
        """;
    private static final String DEACTIVATE_STALE_SQL =
        """
        update catalog_external_asset_identity
           set active = false,
               last_modified_by = ?,
               last_modified_date = ?
         where source_service = ?
           and registration_scope = ?
           and active = true
           and sync_run_id <> ?
        returning asset_type, canonical_asset_key, remote_asset_id
        """;

    private final JdbcTemplate jdbcTemplate;
    private final CodeAssetGrantWriter grantWriter;

    public CatalogExternalAssetIdentityRegistry(
        JdbcTemplate jdbcTemplate,
        CodeAssetGrantWriter grantWriter
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.grantWriter = grantWriter;
    }

    @Transactional
    public RegistrationResult register(
        String sourceService,
        List<Registration> registrations
    ) {
        return register(
            sourceService,
            null,
            null,
            0,
            1,
            false,
            registrations
        );
    }

    @Transactional
    public RegistrationResult register(
        String sourceService,
        String registrationScope,
        UUID syncRunId,
        int batchIndex,
        int batchCount,
        boolean complete,
        List<Registration> registrations
    ) {
        return register(
            sourceService,
            registrationScope,
            syncRunId,
            batchIndex,
            batchCount,
            false,
            complete,
            registrations
        );
    }

    @Transactional
    public RegistrationResult register(
        String sourceService,
        String registrationScope,
        UUID syncRunId,
        int batchIndex,
        int batchCount,
        boolean legacyUnknownBatchCount,
        boolean complete,
        List<Registration> registrations
    ) {
        String source = requireText(
            sourceService,
            64,
            "source service is required"
        );
        String scope = optionalText(registrationScope, 512);
        if (scope != null) {
            scope = scope.toLowerCase(Locale.ROOT);
        }
        if ((scope == null) != (syncRunId == null)) {
            throw new IllegalArgumentException(
                "registration scope and sync run id must be provided together"
            );
        }
        if (complete && scope == null) {
            throw new IllegalArgumentException(
                "completed registration requires a synchronization scope"
            );
        }
        if (legacyUnknownBatchCount && scope == null) {
            throw new IllegalArgumentException(
                "legacy unknown-count synchronization requires a scope"
            );
        }
        if (scope == null && (batchIndex != 0 || batchCount != 1)) {
            throw new IllegalArgumentException(
                "unscoped registration requires one default batch"
            );
        }
        if (scope != null && !legacyUnknownBatchCount) {
            if (
                batchCount <= 0 ||
                batchIndex < 0 ||
                batchIndex >= batchCount
            ) {
                throw new IllegalArgumentException(
                    "invalid synchronization batch position"
                );
            }
            if (complete != (batchIndex == batchCount - 1)) {
                throw new IllegalArgumentException(
                    "completed registration must be the final synchronization batch"
                );
            }
        }
        if (
            scope == null &&
            (registrations == null || registrations.isEmpty()) &&
            !complete
        ) {
            return new RegistrationResult(0, List.of());
        }
        List<Registration> safeRegistrations = registrations == null
            ? List.of()
            : registrations;
        if (safeRegistrations.size() > MAX_REGISTRATIONS) {
            throw new IllegalArgumentException(
                "at most 500 external asset identities can be registered"
            );
        }
        Map<String, NormalizedRegistration> unique = new LinkedHashMap<>();
        for (Registration registration : safeRegistrations) {
            NormalizedRegistration item = normalize(registration);
            if (
                scope != null &&
                !item.canonicalAssetKey().startsWith(scope + "/")
            ) {
                throw new IllegalArgumentException(
                    "external asset identity is outside the synchronization scope"
                );
            }
            String identityKey =
                item.type().name() + "\u0000" + item.canonicalAssetKey();
            NormalizedRegistration previous = unique.putIfAbsent(
                identityKey,
                item
            );
            if (previous != null && !previous.equals(item)) {
                throw new IllegalArgumentException(
                    "conflicting duplicate external asset identity"
                );
            }
        }
        List<NormalizedRegistration> normalized = List.copyOf(unique.values());
        Map<String, String> remoteIdentities = new LinkedHashMap<>();
        for (NormalizedRegistration item : normalized) {
            String remoteIdentityKey =
                item.type().name() + "\u0000" + item.remoteAssetId();
            String previousCanonicalKey = remoteIdentities.putIfAbsent(
                remoteIdentityKey,
                item.canonicalAssetKey()
            );
            if (
                previousCanonicalKey != null &&
                !previousCanonicalKey.equals(item.canonicalAssetKey())
            ) {
                throw new IllegalArgumentException(
                    "conflicting duplicate remote asset identity"
                );
            }
        }
        Instant now = Instant.now();
        int effectiveBatchCount = legacyUnknownBatchCount
            ? LEGACY_UNKNOWN_BATCH_COUNT
            : batchCount;
        BatchDisposition disposition = scope == null
            ? BatchDisposition.PROCESS
            : prepareSyncBatch(
                source,
                scope,
                syncRunId,
                batchIndex,
                effectiveBatchCount,
                legacyUnknownBatchCount,
                now
            );
        if (disposition == BatchDisposition.REPLAY) {
            return result(normalized);
        }
        if (scope == null) {
            List<Object[]> arguments = new ArrayList<>(
                normalized.size()
            );
            for (NormalizedRegistration item : normalized) {
                arguments.add(
                    new Object[] {
                        UUID.randomUUID(),
                        item.type().name(),
                        item.canonicalAssetKey(),
                        item.remoteAssetId(),
                        source,
                        null,
                        null,
                        item.ownerDept(),
                        source,
                        Timestamp.from(now),
                        source,
                        Timestamp.from(now),
                    }
                );
            }
            if (!arguments.isEmpty()) {
                jdbcTemplate.batchUpdate(UPSERT_SQL, arguments);
            }
            for (NormalizedRegistration item : normalized) {
                synchronizeGrant(
                    item.type(),
                    item.canonicalAssetKey(),
                    item.remoteAssetId(),
                    item.ownerDept(),
                    source,
                    "ACTIVE"
                );
            }
        } else {
            stageIdentities(
                source,
                scope,
                syncRunId,
                normalized,
                now
            );
        }
        if (scope != null && complete) {
            mergeCurrentIdentities(source, scope, syncRunId);
            deactivateStaleIdentities(
                source,
                scope,
                syncRunId,
                now
            );
            completeSyncRun(
                source,
                scope,
                syncRunId,
                batchIndex,
                effectiveBatchCount,
                legacyUnknownBatchCount,
                now
            );
        } else if (scope != null) {
            advanceSyncRun(
                source,
                scope,
                syncRunId,
                batchIndex,
                effectiveBatchCount,
                legacyUnknownBatchCount,
                now
            );
        }
        return result(normalized);
    }

    private BatchDisposition prepareSyncBatch(
        String source,
        String scope,
        UUID syncRunId,
        int batchIndex,
        int batchCount,
        boolean legacyUnknownBatchCount,
        Instant now
    ) {
        Timestamp timestamp = Timestamp.from(now);
        jdbcTemplate.update(
            INSERT_SYNC_STATE_SQL,
            source,
            scope,
            timestamp
        );
        List<SyncState> states = jdbcTemplate.query(
            SELECT_SYNC_STATE_SQL,
            (resultSet, rowNumber) ->
                new SyncState(
                    (UUID) resultSet.getObject("active_run_id"),
                    resultSet.getInt("next_batch_index"),
                    (Integer) resultSet.getObject("batch_count"),
                    (UUID) resultSet.getObject(
                        "last_completed_run_id"
                    )
                ),
            source,
            scope
        );
        if (states.size() != 1) {
            throw new IllegalStateException(
                "external asset synchronization state is unavailable"
            );
        }
        SyncState state = states.getFirst();
        SyncRunState run = findSyncRun(
            source,
            scope,
            syncRunId
        );
        if (run != null) {
            if (run.status() == SyncRunStatus.COMPLETED) {
                if (batchCount != run.batchCount()) {
                    throw new IllegalArgumentException(
                        "synchronization batch count changed during the run"
                    );
                }
                return BatchDisposition.REPLAY;
            }
            if (run.status() == SyncRunStatus.SUPERSEDED) {
                throw new IllegalArgumentException(
                    "synchronization run was superseded"
                );
            }
            if (batchCount != run.batchCount()) {
                throw new IllegalArgumentException(
                    "synchronization batch count changed during the run"
                );
            }
            if (
                !syncRunId.equals(state.activeRunId()) ||
                !Integer.valueOf(run.batchCount()).equals(
                    state.batchCount()
                )
            ) {
                throw new IllegalStateException(
                    "active synchronization run does not match its scope state"
                );
            }
            if (legacyUnknownBatchCount) {
                return BatchDisposition.PROCESS;
            }
            if (batchIndex > state.nextBatchIndex()) {
                throw new IllegalArgumentException(
                    "synchronization batches must arrive in order"
                );
            }
            return batchIndex < state.nextBatchIndex()
                ? BatchDisposition.REPLAY
                : BatchDisposition.PROCESS;
        }
        if (!legacyUnknownBatchCount && batchIndex != 0) {
            throw new IllegalArgumentException(
                "synchronization must start with batch zero"
            );
        }
        if (state.activeRunId() != null) {
            supersedeSyncRun(
                source,
                scope,
                state.activeRunId(),
                timestamp
            );
            deleteStagedItems(
                source,
                scope,
                state.activeRunId()
            );
        }
        insertSyncRun(
            source,
            scope,
            syncRunId,
            batchCount,
            timestamp
        );
        claimSyncRun(
            source,
            scope,
            syncRunId,
            batchCount,
            timestamp
        );
        return BatchDisposition.PROCESS;
    }

    private SyncRunState findSyncRun(
        String source,
        String scope,
        UUID syncRunId
    ) {
        List<SyncRunState> runs = jdbcTemplate.query(
            SELECT_SYNC_RUN_SQL,
            (resultSet, rowNumber) ->
                new SyncRunState(
                    SyncRunStatus.valueOf(
                        resultSet.getString("status")
                    ),
                    resultSet.getInt("batch_count")
                ),
            source,
            scope,
            syncRunId
        );
        if (runs.size() > 1) {
            throw new IllegalStateException(
                "external asset synchronization run is not unique"
            );
        }
        return runs.isEmpty() ? null : runs.getFirst();
    }

    private void insertSyncRun(
        String source,
        String scope,
        UUID syncRunId,
        int batchCount,
        Timestamp timestamp
    ) {
        requireStateUpdate(
            jdbcTemplate.update(
                INSERT_SYNC_RUN_SQL,
                source,
                scope,
                syncRunId,
                batchCount,
                timestamp,
                timestamp
            )
        );
    }

    private void supersedeSyncRun(
        String source,
        String scope,
        UUID syncRunId,
        Timestamp timestamp
    ) {
        requireStateUpdate(
            jdbcTemplate.update(
                SUPERSEDE_SYNC_RUN_SQL,
                timestamp,
                timestamp,
                source,
                scope,
                syncRunId
            )
        );
    }

    private void claimSyncRun(
        String source,
        String scope,
        UUID syncRunId,
        int batchCount,
        Timestamp timestamp
    ) {
        requireStateUpdate(
            jdbcTemplate.update(
                CLAIM_SYNC_RUN_SQL,
                syncRunId,
                batchCount,
                timestamp,
                source,
                scope
            )
        );
    }

    private void advanceSyncRun(
        String source,
        String scope,
        UUID syncRunId,
        int batchIndex,
        int batchCount,
        boolean legacyUnknownBatchCount,
        Instant now
    ) {
        if (legacyUnknownBatchCount) {
            requireStateUpdate(
                jdbcTemplate.update(
                    ADVANCE_LEGACY_SYNC_RUN_SQL,
                    Timestamp.from(now),
                    source,
                    scope,
                    syncRunId,
                    batchCount
                )
            );
            return;
        }
        requireStateUpdate(
            jdbcTemplate.update(
                ADVANCE_SYNC_RUN_SQL,
                batchIndex + 1,
                Timestamp.from(now),
                source,
                scope,
                syncRunId,
                batchIndex,
                batchCount
            )
        );
    }

    private void completeSyncRun(
        String source,
        String scope,
        UUID syncRunId,
        int batchIndex,
        int batchCount,
        boolean legacyUnknownBatchCount,
        Instant now
    ) {
        Timestamp timestamp = Timestamp.from(now);
        deleteStagedItems(source, scope, syncRunId);
        requireStateUpdate(
            jdbcTemplate.update(
                COMPLETE_SYNC_RUN_RECORD_SQL,
                timestamp,
                timestamp,
                source,
                scope,
                syncRunId
            )
        );
        int updated = legacyUnknownBatchCount
            ? jdbcTemplate.update(
                COMPLETE_LEGACY_SYNC_RUN_SQL,
                syncRunId,
                timestamp,
                source,
                scope,
                syncRunId,
                batchCount
            )
            : jdbcTemplate.update(
                COMPLETE_SYNC_RUN_SQL,
                syncRunId,
                timestamp,
                source,
                scope,
                syncRunId,
                batchIndex,
                batchCount
            );
        requireStateUpdate(updated);
    }

    private void requireStateUpdate(int updated) {
        if (updated != 1) {
            throw new IllegalStateException(
                "external asset synchronization state changed concurrently"
            );
        }
    }

    private void stageIdentities(
        String source,
        String scope,
        UUID syncRunId,
        List<NormalizedRegistration> normalized,
        Instant now
    ) {
        Timestamp timestamp = Timestamp.from(now);
        List<Object[]> arguments = new ArrayList<>(
            normalized.size()
        );
        for (NormalizedRegistration item : normalized) {
            arguments.add(
                new Object[] {
                    UUID.randomUUID(),
                    source,
                    scope,
                    syncRunId,
                    item.type().name(),
                    item.canonicalAssetKey(),
                    item.remoteAssetId(),
                    item.ownerDept(),
                    timestamp,
                    timestamp,
                }
            );
        }
        if (!arguments.isEmpty()) {
            jdbcTemplate.batchUpdate(STAGE_SQL, arguments);
        }
    }

    private void mergeCurrentIdentities(
        String source,
        String scope,
        UUID syncRunId
    ) {
        List<ActiveIdentity> current = jdbcTemplate.query(
            MERGE_CURRENT_SQL,
            (resultSet, rowNumber) ->
                new ActiveIdentity(
                    CatalogAssetType.from(
                        resultSet.getString("asset_type")
                    ),
                    resultSet.getString("canonical_asset_key"),
                    resultSet.getString("remote_asset_id"),
                    resultSet.getString("owner_dept")
                ),
            source,
            scope,
            syncRunId
        );
        for (ActiveIdentity item : current) {
            synchronizeGrant(
                item.type(),
                item.canonicalAssetKey(),
                item.remoteAssetId(),
                item.ownerDept(),
                source,
                "ACTIVE"
            );
        }
    }

    private void deleteStagedItems(
        String source,
        String scope,
        UUID syncRunId
    ) {
        jdbcTemplate.update(
            DELETE_STAGED_ITEMS_SQL,
            source,
            scope,
            syncRunId
        );
    }

    private RegistrationResult result(
        List<NormalizedRegistration> normalized
    ) {
        return new RegistrationResult(
            normalized.size(),
            normalized
                .stream()
                .map(NormalizedRegistration::canonicalAssetKey)
                .toList()
        );
    }

    private void deactivateStaleIdentities(
        String source,
        String scope,
        UUID syncRunId,
        Instant now
    ) {
        List<StaleIdentity> stale = jdbcTemplate.query(
            DEACTIVATE_STALE_SQL,
            (resultSet, rowNumber) ->
                new StaleIdentity(
                    CatalogAssetType.from(
                        resultSet.getString("asset_type")
                    ),
                    resultSet.getString("canonical_asset_key"),
                    resultSet.getString("remote_asset_id")
                ),
            source,
            Timestamp.from(now),
            source,
            scope,
            syncRunId
        );
        for (StaleIdentity item : stale) {
            synchronizeGrant(
                item.type(),
                item.canonicalAssetKey(),
                item.remoteAssetId(),
                null,
                source,
                "INACTIVE"
            );
        }
    }

    private void synchronizeGrant(
        CatalogAssetType type,
        String canonicalAssetKey,
        String remoteAssetId,
        String ownerDept,
        String source,
        String status
    ) {
        grantWriter.synchronizeExternalCodeAsset(
            new CatalogAssetIdentity(
                type,
                canonicalAssetKey,
                remoteAssetId,
                source + ":" + remoteAssetId
            ),
            ownerDept,
            source,
            "INTERNAL",
            status
        );
    }

    private NormalizedRegistration normalize(Registration registration) {
        if (registration == null) {
            throw new IllegalArgumentException("registration is required");
        }
        CatalogAssetType type = CatalogAssetType.from(registration.assetType());
        String key = requireText(
            registration.canonicalAssetKey(),
            512,
            "canonical asset key is required"
        ).toLowerCase(Locale.ROOT);
        String remoteAssetId = requireText(
            registration.remoteAssetId(),
            128,
            "remote asset id is required"
        );
        String canonical = switch (type) {
            case METRIC -> canonicalMetricKey(key);
            case METRIC_PACK -> canonicalMetricPackKey(key);
            default ->
                throw new IllegalArgumentException(
                    "external identity registration only supports METRIC and METRIC_PACK"
                );
        };
        if (!canonical.equals(key)) {
            throw new IllegalArgumentException(
                "canonical asset key did not round-trip"
            );
        }
        return new NormalizedRegistration(
            type,
            canonical,
            remoteAssetId,
            optionalText(registration.ownerDept(), 128)
        );
    }

    private String canonicalMetricKey(String key) {
        Matcher matcher = METRIC_KEY.matcher(key);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid metric asset key");
        }
        return CatalogAssetKey.metric(
            matcher.group(1),
            matcher.group(2),
            matcher.group(3)
        );
    }

    private String canonicalMetricPackKey(String key) {
        Matcher matcher = METRIC_PACK_KEY.matcher(key);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid metric pack asset key");
        }
        return CatalogAssetKey.metricPack(
            matcher.group(1),
            matcher.group(2),
            matcher.group(3)
        );
    }

    private String requireText(String value, int maxLength, String message) {
        String normalized = optionalText(value, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String optionalText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                "value exceeds " + maxLength + " characters"
            );
        }
        return normalized;
    }

    public record Registration(
        String assetType,
        String canonicalAssetKey,
        String remoteAssetId,
        String ownerDept
    ) {}

    public record RegistrationResult(
        int registered,
        List<String> canonicalAssetKeys
    ) {}

    private record NormalizedRegistration(
        CatalogAssetType type,
        String canonicalAssetKey,
        String remoteAssetId,
        String ownerDept
    ) {}

    private record StaleIdentity(
        CatalogAssetType type,
        String canonicalAssetKey,
        String remoteAssetId
    ) {}

    private record ActiveIdentity(
        CatalogAssetType type,
        String canonicalAssetKey,
        String remoteAssetId,
        String ownerDept
    ) {}

    private record SyncState(
        UUID activeRunId,
        int nextBatchIndex,
        Integer batchCount,
        UUID lastCompletedRunId
    ) {}

    private record SyncRunState(
        SyncRunStatus status,
        int batchCount
    ) {}

    private enum BatchDisposition {
        PROCESS,
        REPLAY,
    }

    private enum SyncRunStatus {
        ACTIVE,
        COMPLETED,
        SUPERSEDED,
    }
}
