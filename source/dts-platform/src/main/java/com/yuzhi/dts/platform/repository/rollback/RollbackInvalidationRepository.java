package com.yuzhi.dts.platform.repository.rollback;

import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationException;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class RollbackInvalidationRepository {

    public static final String AVAILABLE = "AVAILABLE";
    public static final String FENCED = "FENCED";
    public static final String UNAVAILABLE = "UNAVAILABLE";

    private static final long SOURCE_SEQUENCE_LOCK_ID = 7_363_310_424_306_006_369L;

    private final JdbcTemplate jdbcTemplate;

    public RollbackInvalidationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long nextSourceSequence() {
        acquireSourceSequenceLock();
        Long value = jdbcTemplate.queryForObject(
            "select nextval('integration_rollback_invalidation_source_seq')",
            Long.class
        );
        if (value == null) {
            throw new IllegalStateException("Rollback invalidation source sequence is unavailable");
        }
        return value;
    }

    public void advanceSourceSequence(long observedSequence) {
        if (observedSequence < 1L || observedSequence == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Rollback invalidation source sequence is out of range");
        }
        acquireSourceSequenceLock();
        jdbcTemplate.queryForObject(
            """
            select setval(
                'integration_rollback_invalidation_source_seq',
                greatest(
                    (select last_value from integration_rollback_invalidation_source_seq),
                    ?
                ),
                true
            )
            """,
            Long.class,
            observedSequence
        );
    }

    public Optional<Receipt> findReceiptByIdempotencyKey(String idempotencyKey) {
        return queryReceipt(
            "select " + RECEIPT_COLUMNS + " from integration_rollback_invalidation_receipt where idempotency_key = ?",
            idempotencyKey
        );
    }

    public Optional<Receipt> findReceiptForUpdate(UUID receiptId) {
        return queryReceipt(
            "select " + RECEIPT_COLUMNS + " from integration_rollback_invalidation_receipt where id = ? for update",
            receiptId
        );
    }

    public Optional<Receipt> findReceipt(UUID receiptId) {
        return queryReceipt(
            "select " + RECEIPT_COLUMNS + " from integration_rollback_invalidation_receipt where id = ?",
            receiptId
        );
    }

    public boolean insertReceipt(Receipt receipt, Instant now) {
        return jdbcTemplate.update(
            """
            insert into integration_rollback_invalidation_receipt (
                id, idempotency_key, payload_hash, state, rollback_level, rollback_scope,
                task_id, source_data_source_id, source_sequence, actor,
                prepared_at, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (idempotency_key) do nothing
            """,
            receipt.id(),
            receipt.idempotencyKey(),
            receipt.payloadHash(),
            receipt.state(),
            receipt.level(),
            receipt.scope(),
            receipt.taskId(),
            receipt.sourceDataSourceId(),
            receipt.sourceSequence(),
            receipt.actor(),
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(now)
        ) == 1;
    }

    public List<Target> discoverTargets(UUID sourceDataSourceId, List<String> requestedTables) {
        List<TargetSeed> seeds = jdbcTemplate.query(
            """
            select m.id as ods_mapping_id, m.dataset_id, m.enabled as mapping_enabled,
                   m.stream_name, m.stream_namespace, m.ods_schema, m.ods_table,
                   d.source_id as dataset_source_id, d.hive_database, d.hive_table, d.name as dataset_name
              from infra_ods_table_mapping m
              left join catalog_dataset d on d.id = m.dataset_id
             where m.connection_id = ?
             order by m.created_date, m.id
            """,
            (row, rowNumber) ->
                new TargetSeed(
                    row.getObject("ods_mapping_id", UUID.class),
                    row.getObject("dataset_id", UUID.class),
                    row.getBoolean("mapping_enabled"),
                    row.getString("stream_name"),
                    row.getString("stream_namespace"),
                    row.getString("ods_schema"),
                    row.getString("ods_table"),
                    row.getObject("dataset_source_id", UUID.class),
                    row.getString("hive_database"),
                    row.getString("hive_table"),
                    row.getString("dataset_name")
                ),
            sourceDataSourceId
        );
        Set<String> filters = normalizeTables(requestedTables);
        if (requestedTables != null && requestedTables.stream().anyMatch(table -> !StringUtils.hasText(table))) {
            throw RollbackInvalidationException.unresolvedTarget("Rollback target table names must not be blank");
        }
        if (seeds.isEmpty()) {
            throw RollbackInvalidationException.unresolvedTarget(
                "No ODS mapping is available for rollback source " + sourceDataSourceId
            );
        }
        if (!filters.isEmpty()) {
            Set<String> missing = filters
                .stream()
                .filter(filter -> seeds.stream().noneMatch(seed -> candidates(seed).contains(filter)))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (!missing.isEmpty()) {
                throw RollbackInvalidationException.unresolvedTarget(
                    "Rollback target tables have no ODS mapping: " + String.join(", ", missing)
                );
            }
        }
        List<TargetSeed> selected = filters.isEmpty()
            ? List.copyOf(seeds)
            : seeds.stream().filter(seed -> matches(seed, filters)).toList();

        selected.forEach(seed -> requireMatchingDatasetSource(sourceDataSourceId, seed));
        Map<AssetIdentity, AvailabilitySnapshot> availability = loadAvailability(
            selected
                .stream()
                .map(seed -> assetIdentity(sourceDataSourceId, seed))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
        );
        List<Target> targets = new ArrayList<>(selected.size());
        for (TargetSeed seed : selected) {
            AssetIdentity asset = assetIdentity(sourceDataSourceId, seed);
            AvailabilitySnapshot previous = availability.getOrDefault(asset, AvailabilitySnapshot.legacyAvailable());
            if (!AVAILABLE.equals(previous.status())) {
                throw RollbackInvalidationException.conflict(
                    "Rollback target is already fenced or unavailable: " + asset.assetKey()
                );
            }
            targets.add(
                new Target(
                    UUID.randomUUID(),
                    null,
                    asset.assetType(),
                    asset.assetKey(),
                    seed.odsMappingId(),
                    seed.datasetId(),
                    seed.mappingEnabled(),
                    previous
                )
            );
        }
        return List.copyOf(targets);
    }

    private void requireMatchingDatasetSource(UUID sourceDataSourceId, TargetSeed seed) {
        if (seed.datasetSourceId() != null && !sourceDataSourceId.equals(seed.datasetSourceId())) {
            throw RollbackInvalidationException.unresolvedTarget(
                "Rollback target dataset source does not match its ODS mapping: " + seed.odsMappingId()
            );
        }
    }

    public void insertTargets(UUID receiptId, List<Target> targets, Instant now) {
        if (targets.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
            """
            insert into integration_rollback_invalidation_target (
                id, receipt_id, asset_type, asset_key, ods_mapping_id, dataset_id,
                previous_mapping_enabled, previous_availability_status,
                previous_availability_epoch, previous_availability_event_id,
                previous_availability_payload_hash, previous_availability_reason,
                previous_availability_source_sequence, created_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement statement, int index) throws java.sql.SQLException {
                    Target target = targets.get(index);
                    statement.setObject(1, target.id());
                    statement.setObject(2, receiptId);
                    statement.setString(3, target.assetType());
                    statement.setString(4, target.assetKey());
                    statement.setObject(5, target.odsMappingId());
                    statement.setObject(6, target.datasetId());
                    statement.setBoolean(7, target.previousMappingEnabled());
                    statement.setString(8, target.previousAvailability().status());
                    statement.setLong(9, target.previousAvailability().epoch());
                    statement.setString(10, target.previousAvailability().eventId());
                    statement.setString(11, target.previousAvailability().payloadHash());
                    statement.setString(12, target.previousAvailability().reason());
                    statement.setLong(13, target.previousAvailability().sourceSequence());
                    statement.setTimestamp(14, Timestamp.from(now));
                }

                @Override
                public int getBatchSize() {
                    return targets.size();
                }
            }
        );
    }

    public void fenceTargets(
        UUID receiptId,
        long sourceSequence,
        String eventId,
        String payloadHash,
        List<Target> targets,
        Instant now
    ) {
        Set<AssetIdentity> assets = new LinkedHashSet<>();
        for (Target target : targets) {
            assets.add(new AssetIdentity(target.assetType(), target.assetKey()));
            requireOne(
                jdbcTemplate.update(
                    """
                    update infra_ods_table_mapping
                       set enabled = false, availability_fence_id = ?, availability_event_id = ?,
                           availability_source_sequence = ?, last_modified_date = ?
                     where id = ? and availability_fence_id is null and availability_source_sequence = ?
                    """,
                    receiptId,
                    eventId,
                    sourceSequence,
                    Timestamp.from(now),
                    target.odsMappingId(),
                    target.previousAvailability().sourceSequence()
                ),
                "A newer ODS availability event already owns the rollback target"
            );
        }
        for (AssetIdentity asset : assets) {
            writeAvailabilityCurrent(
                asset,
                FENCED,
                targetPreviousEpoch(targets, asset),
                sourceSequence,
                receiptId,
                eventId,
                payloadHash,
                "ROLLBACK_PREPARED",
                now
            );
            insertAvailabilityEvent(
                receiptId,
                asset,
                FENCED,
                targetPreviousEpoch(targets, asset),
                sourceSequence,
                eventId,
                payloadHash,
                "ROLLBACK_PREPARED",
                now
            );
        }
    }

    public List<Target> findTargets(UUID receiptId) {
        return jdbcTemplate.query(
            """
            select id, receipt_id, asset_type, asset_key, ods_mapping_id, dataset_id,
                   previous_mapping_enabled, previous_availability_status,
                   previous_availability_epoch, previous_availability_event_id,
                   previous_availability_payload_hash, previous_availability_reason,
                   previous_availability_source_sequence
              from integration_rollback_invalidation_target
             where receipt_id = ?
             order by created_at, id
            """,
            (row, rowNumber) ->
                new Target(
                    row.getObject("id", UUID.class),
                    row.getObject("receipt_id", UUID.class),
                    row.getString("asset_type"),
                    row.getString("asset_key"),
                    row.getObject("ods_mapping_id", UUID.class),
                    row.getObject("dataset_id", UUID.class),
                    row.getBoolean("previous_mapping_enabled"),
                    new AvailabilitySnapshot(
                        row.getString("previous_availability_status"),
                        row.getLong("previous_availability_epoch"),
                        row.getLong("previous_availability_source_sequence"),
                        row.getString("previous_availability_event_id"),
                        row.getString("previous_availability_payload_hash"),
                        row.getString("previous_availability_reason")
                    )
                ),
            receiptId
        );
    }

    public void applyTargets(
        Receipt receipt,
        List<Target> targets,
        String eventId,
        String payloadHash,
        long sourceSequence,
        String reason,
        Instant now
    ) {
        Set<AssetIdentity> assets = uniqueAssets(targets);
        for (AssetIdentity asset : assets) {
            long epoch = targetPreviousEpoch(targets, asset) + 1L;
            writeAvailabilityCurrent(
                asset,
                UNAVAILABLE,
                epoch,
                sourceSequence,
                receipt.id(),
                eventId,
                payloadHash,
                reason,
                now
            );
            insertAvailabilityEvent(receipt.id(), asset, UNAVAILABLE, epoch, sourceSequence, eventId, payloadHash, reason, now);
        }
        projectMappings(receipt.id(), targets, false, eventId, sourceSequence, now, false);
        jdbcTemplate.update(
            "update integration_rollback_invalidation_target set applied_at = ? where receipt_id = ? and applied_at is null",
            Timestamp.from(now),
            receipt.id()
        );
    }

    public void abortTargets(
        Receipt receipt,
        List<Target> targets,
        String eventId,
        String payloadHash,
        long sourceSequence,
        String reason,
        Instant now
    ) {
        Set<AssetIdentity> assets = uniqueAssets(targets);
        for (AssetIdentity asset : assets) {
            long epoch = targetPreviousEpoch(targets, asset);
            writeAvailabilityCurrent(
                asset,
                AVAILABLE,
                epoch,
                sourceSequence,
                null,
                eventId,
                payloadHash,
                reason,
                now
            );
            insertAvailabilityEvent(receipt.id(), asset, AVAILABLE, epoch, sourceSequence, eventId, payloadHash, reason, now);
        }
        projectMappings(receipt.id(), targets, true, eventId, sourceSequence, now, true);
    }

    public void restoreTargets(
        Receipt receipt,
        List<Target> targets,
        String eventId,
        String payloadHash,
        long sourceSequence,
        String reason,
        Instant now
    ) {
        Set<AssetIdentity> assets = uniqueAssets(targets);
        for (AssetIdentity asset : assets) {
            long epoch = targetPreviousEpoch(targets, asset) + 2L;
            writeAvailabilityCurrent(
                asset,
                AVAILABLE,
                epoch,
                sourceSequence,
                null,
                eventId,
                payloadHash,
                reason,
                now
            );
            insertAvailabilityEvent(receipt.id(), asset, AVAILABLE, epoch, sourceSequence, eventId, payloadHash, reason, now);
        }
        projectMappings(receipt.id(), targets, true, eventId, sourceSequence, now, true);
    }

    public void updateReceiptState(
        UUID receiptId,
        String state,
        String eventId,
        String payloadHash,
        Long sourceSequence,
        String downstreamSummaryJson,
        Instant now
    ) {
        boolean reconciliation = "RECONCILIATION_REQUIRED".equals(state);
        if (
            (reconciliation && (eventId != null || payloadHash != null || sourceSequence != null)) ||
            (!reconciliation && (!StringUtils.hasText(eventId) || !StringUtils.hasText(payloadHash) || sourceSequence == null))
        ) {
            throw new IllegalArgumentException("Rollback receipt transition evidence is incomplete");
        }
        String timestampColumn = switch (state) {
            case "APPLIED" -> "applied_at";
            case "ABORTED" -> "aborted_at";
            case "RESTORED" -> "restored_at";
            case "RECONCILIATION_REQUIRED" -> "reconciliation_at";
            default -> throw new IllegalArgumentException("Unsupported receipt state: " + state);
        };
        List<String> expectedStates = switch (state) {
            case "RECONCILIATION_REQUIRED" -> List.of("PREPARED");
            case "APPLIED", "ABORTED" -> List.of("PREPARED", "RECONCILIATION_REQUIRED");
            case "RESTORED" -> List.of("APPLIED");
            default -> throw new IllegalArgumentException("Unsupported receipt state: " + state);
        };
        String expectedStatePlaceholders = String.join(",", java.util.Collections.nCopies(expectedStates.size(), "?"));
        String sequenceGuard = sourceSequence == null
            ? " and completion_event_id is null and completion_source_sequence is null"
            : " and greatest(source_sequence, coalesce(completion_source_sequence, source_sequence)) < ?";
        List<Object> arguments = new ArrayList<>();
        arguments.add(state);
        arguments.add(eventId);
        arguments.add(payloadHash);
        arguments.add(sourceSequence);
        arguments.add(downstreamSummaryJson);
        arguments.add(Timestamp.from(now));
        arguments.add(Timestamp.from(now));
        arguments.add(receiptId);
        arguments.addAll(expectedStates);
        if (sourceSequence != null) {
            arguments.add(sourceSequence);
        }
        requireOne(
            jdbcTemplate.update(
                "update integration_rollback_invalidation_receipt set state = ?, completion_event_id = ?, " +
                "completion_payload_hash = ?, completion_source_sequence = ?, downstream_summary_json = ?, " +
                timestampColumn + " = ?, updated_at = ? where id = ? and state in (" +
                expectedStatePlaceholders + ")" + sequenceGuard,
                arguments.toArray()
            ),
            "Rollback invalidation receipt transition or source sequence is stale"
        );
    }

    public Optional<CompletionEvent> findCompletionEvent(UUID receiptId, String eventId) {
        return jdbcTemplate
            .query(
                """
                select receipt_id, event_id, payload_hash, outcome, resulting_state,
                       source_sequence, reason, zero_side_effects_confirmed,
                       downstream_reference, completed_at
                  from integration_rollback_invalidation_completion_event
                 where receipt_id = ? and event_id = ?
                """,
                (row, rowNumber) ->
                    new CompletionEvent(
                        row.getObject("receipt_id", UUID.class),
                        row.getString("event_id"),
                        row.getString("payload_hash"),
                        row.getString("outcome"),
                        row.getString("resulting_state"),
                        row.getLong("source_sequence"),
                        row.getString("reason"),
                        row.getBoolean("zero_side_effects_confirmed"),
                        row.getString("downstream_reference"),
                        row.getTimestamp("completed_at").toInstant()
                    ),
                receiptId,
                eventId
            )
            .stream()
            .findFirst();
    }

    public void insertCompletionEvent(CompletionEvent event) {
        int inserted = jdbcTemplate.update(
            """
            insert into integration_rollback_invalidation_completion_event (
                id, receipt_id, event_id, payload_hash, outcome, resulting_state,
                source_sequence, reason, zero_side_effects_confirmed,
                downstream_reference, completed_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (receipt_id, event_id) do nothing
            """,
            UUID.randomUUID(),
            event.receiptId(),
            event.eventId(),
            event.payloadHash(),
            event.outcome(),
            event.resultingState(),
            event.sourceSequence(),
            truncate(event.reason(), 512),
            event.zeroSideEffectsConfirmed(),
            event.downstreamReference(),
            Timestamp.from(event.completedAt())
        );
        if (inserted == 1) {
            return;
        }
        String existingHash = jdbcTemplate.queryForObject(
            """
            select payload_hash
              from integration_rollback_invalidation_completion_event
             where receipt_id = ? and event_id = ?
            """,
            String.class,
            event.receiptId(),
            event.eventId()
        );
        if (!event.payloadHash().equals(existingHash)) {
            throw RollbackInvalidationException.conflict(
                "Rollback completion event already exists with a different payload: " + event.eventId()
            );
        }
    }

    public void insertDispatch(UUID receiptId, String commandHash, String commandJson, Instant now) {
        int inserted = jdbcTemplate.update(
            """
            insert into integration_rollback_dispatch_outbox (
                id, receipt_id, command_hash, command_json, status, dispatch_attempts,
                next_attempt_at, created_at, updated_at
            ) values (?, ?, ?, ?, 'PENDING', 0, ?, ?, ?)
            on conflict (receipt_id) do nothing
            """,
            receiptId,
            receiptId,
            commandHash,
            commandJson,
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(now)
        );
        if (inserted == 1) {
            return;
        }
        DispatchRecord existing = findDispatch(receiptId)
            .orElseThrow(() -> new IllegalStateException("Rollback dispatch disappeared after idempotency conflict"));
        if (!commandHash.equals(existing.commandHash()) || !commandJson.equals(existing.commandJson())) {
            throw RollbackInvalidationException.conflict(
                "Rollback dispatch receipt already exists with a different command: " + receiptId
            );
        }
    }

    public Optional<DispatchRecord> findDispatch(UUID receiptId) {
        return jdbcTemplate
            .query(
                """
                select id, receipt_id, command_hash, command_json, status, dispatch_attempts,
                       generation_attempts, claimed_at, next_attempt_at, sent_at, completed_at, last_error
                  from integration_rollback_dispatch_outbox
                 where receipt_id = ?
                """,
                (row, rowNumber) -> mapDispatch(row),
                receiptId
            )
            .stream()
            .findFirst();
    }

    public Optional<DispatchRecord> claimDispatch(UUID receiptId, Instant now, Duration staleClaimTtl) {
        requireClaimArguments(now, staleClaimTtl);
        return jdbcTemplate
            .query(
                """
                with next_dispatch as (
                    select d.id
                      from integration_rollback_dispatch_outbox d
                      join integration_rollback_invalidation_receipt r on r.id = d.receipt_id
                     where d.receipt_id = ?
                       and r.state in ('PREPARED', 'RECONCILIATION_REQUIRED')
                       and (
                            (
                                d.status in ('PENDING', 'RETRY', 'SENT')
                                and d.next_attempt_at <= ?
                            )
                            or (d.status = 'CLAIMED' and d.claimed_at < ?)
                       )
                     for update of d skip locked
                )
                update integration_rollback_dispatch_outbox d
                   set status = 'CLAIMED', claimed_at = ?,
                       dispatch_attempts = dispatch_attempts + 1,
                       generation_attempts = generation_attempts + 1, updated_at = ?
                  from next_dispatch n
                 where d.id = n.id
                returning d.id, d.receipt_id, d.command_hash, d.command_json,
                          d.status, d.dispatch_attempts, d.generation_attempts,
                          d.claimed_at, d.next_attempt_at,
                          d.sent_at, d.completed_at, d.last_error
                """,
                (row, rowNumber) -> mapDispatch(row),
                receiptId,
                Timestamp.from(now),
                Timestamp.from(now.minus(staleClaimTtl)),
                Timestamp.from(now),
                Timestamp.from(now)
            )
            .stream()
            .findFirst();
    }

    public Optional<DispatchRecord> claimNextDispatch(Instant now, Duration staleClaimTtl) {
        requireClaimArguments(now, staleClaimTtl);
        return jdbcTemplate
            .query(
                """
                with next_dispatch as (
                    select d.id
                      from integration_rollback_dispatch_outbox d
                      join integration_rollback_invalidation_receipt r on r.id = d.receipt_id
                     where r.state in ('PREPARED', 'RECONCILIATION_REQUIRED')
                       and (
                            (
                                d.status in ('PENDING', 'RETRY', 'SENT')
                                and d.next_attempt_at <= ?
                            )
                            or (d.status = 'CLAIMED' and d.claimed_at < ?)
                       )
                     order by d.next_attempt_at, d.created_at, d.id
                     for update of d skip locked
                     limit 1
                )
                update integration_rollback_dispatch_outbox d
                   set status = 'CLAIMED', claimed_at = ?,
                       dispatch_attempts = dispatch_attempts + 1,
                       generation_attempts = generation_attempts + 1, updated_at = ?
                  from next_dispatch n
                 where d.id = n.id
                returning d.id, d.receipt_id, d.command_hash, d.command_json,
                          d.status, d.dispatch_attempts, d.generation_attempts,
                          d.claimed_at, d.next_attempt_at,
                          d.sent_at, d.completed_at, d.last_error
                """,
                (row, rowNumber) -> mapDispatch(row),
                Timestamp.from(now),
                Timestamp.from(now.minus(staleClaimTtl)),
                Timestamp.from(now),
                Timestamp.from(now)
            )
            .stream()
            .findFirst();
    }

    public void markDispatchSent(UUID receiptId, int claimAttempt, Instant nextAttemptAt, Instant now) {
        int changed = jdbcTemplate.update(
            """
            update integration_rollback_dispatch_outbox
               set status = 'SENT', claimed_at = null, next_attempt_at = ?,
                   sent_at = coalesce(sent_at, ?), last_error = null, updated_at = ?
             where receipt_id = ? and status = 'CLAIMED' and dispatch_attempts = ?
            """,
            Timestamp.from(nextAttemptAt),
            Timestamp.from(now),
            Timestamp.from(now),
            receiptId,
            claimAttempt
        );
        requireDispatchTransition(changed, receiptId, "Rollback dispatch sent state is stale");
    }

    public void markDispatchRetry(
        UUID receiptId,
        int claimAttempt,
        String error,
        Instant nextAttemptAt,
        Instant now
    ) {
        int changed = jdbcTemplate.update(
            """
            update integration_rollback_dispatch_outbox
               set status = 'RETRY', claimed_at = null, next_attempt_at = ?,
                   last_error = ?, updated_at = ?
             where receipt_id = ? and status = 'CLAIMED' and dispatch_attempts = ?
            """,
            Timestamp.from(nextAttemptAt),
            truncate(error, 512),
            Timestamp.from(now),
            receiptId,
            claimAttempt
        );
        requireDispatchTransition(changed, receiptId, "Rollback dispatch retry state is stale");
    }

    public void markDispatchDead(UUID receiptId, int claimAttempt, String error, Instant now) {
        int changed = jdbcTemplate.update(
            """
            update integration_rollback_dispatch_outbox
               set status = 'DEAD', claimed_at = null, next_attempt_at = null,
                   last_error = ?, updated_at = ?
             where receipt_id = ? and status = 'CLAIMED' and dispatch_attempts = ?
            """,
            truncate(error, 512),
            Timestamp.from(now),
            receiptId,
            claimAttempt
        );
        requireDispatchTransition(changed, receiptId, "Rollback dispatch dead state is stale");
    }

    public void markDispatchCompletedByCallback(UUID receiptId, Instant now) {
        int changed = jdbcTemplate.update(
            """
            update integration_rollback_dispatch_outbox
               set status = 'COMPLETED', claimed_at = null, next_attempt_at = null,
                   completed_at = coalesce(completed_at, ?), last_error = null, updated_at = ?
             where receipt_id = ? and status <> 'COMPLETED'
            """,
            Timestamp.from(now),
            Timestamp.from(now),
            receiptId
        );
        if (changed == 0 && !hasDispatchStatus(receiptId, "COMPLETED")) {
            throw new IllegalStateException("Rollback completion has no durable dispatch row");
        }
    }

    public void replayDispatch(UUID receiptId, Instant now) {
        requireOne(
            jdbcTemplate.update(
                """
                update integration_rollback_dispatch_outbox
                   set status = 'PENDING', claimed_at = null, next_attempt_at = ?,
                       generation_attempts = 0, last_error = null, updated_at = ?
                 where receipt_id = ? and status in ('RETRY', 'SENT', 'DEAD')
                """,
                Timestamp.from(now),
                Timestamp.from(now),
                receiptId
            ),
            "Rollback dispatch is not replayable"
        );
    }

    public void insertPlatformEvent(
        String eventId,
        String payloadHash,
        String eventType,
        String aggregateId,
        String action,
        String status,
        String actor,
        String auditActionCode,
        String payloadJson,
        Instant now
    ) {
        int inserted = jdbcTemplate.update(
            """
            insert into platform_event_outbox (
                id, event_id, event_type, domain, source_app, aggregate_type, aggregate_id,
                action, severity, status, occurred_at, actor, audit_action_code,
                payload_json, payload_hash, dispatch_status, dispatch_attempts,
                created_by, created_date, last_modified_by, last_modified_date
            ) values (?, ?, ?, 'integration', 'dts-platform', 'ROLLBACK_INVALIDATION', ?,
                      ?, 'WARN', ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?, ?)
            on conflict (event_id) do nothing
            """,
            UUID.randomUUID(),
            eventId,
            eventType,
            aggregateId,
            action,
            status,
            Timestamp.from(now),
            actor,
            auditActionCode,
            payloadJson,
            payloadHash,
            actor,
            Timestamp.from(now),
            actor,
            Timestamp.from(now)
        );
        if (inserted == 1) {
            return;
        }
        String existingHash = jdbcTemplate.queryForObject(
            "select payload_hash from platform_event_outbox where event_id = ?",
            String.class,
            eventId
        );
        if (!payloadHash.equals(existingHash)) {
            throw RollbackInvalidationException.conflict(
                "Platform event id already exists with a different payload: " + eventId
            );
        }
    }

    private Optional<Receipt> queryReceipt(String sql, Object argument) {
        return jdbcTemplate
            .query(
                sql,
                (row, rowNumber) ->
                    new Receipt(
                        row.getObject("id", UUID.class),
                        row.getString("idempotency_key"),
                        row.getString("payload_hash"),
                        row.getString("state"),
                        row.getInt("rollback_level"),
                        row.getString("rollback_scope"),
                        row.getObject("task_id", Long.class),
                        row.getObject("source_data_source_id", UUID.class),
                        row.getLong("source_sequence"),
                        row.getString("actor"),
                        row.getString("completion_event_id"),
                        row.getString("completion_payload_hash"),
                        row.getObject("completion_source_sequence", Long.class)
                    ),
                argument
            )
            .stream()
            .findFirst();
    }

    private Map<AssetIdentity, AvailabilitySnapshot> loadAvailability(Set<AssetIdentity> assets) {
        if (assets.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(assets.size(), "?"));
        List<Object> arguments = new ArrayList<>();
        arguments.add(CatalogAssetType.DATASET.name());
        assets.forEach(asset -> arguments.add(asset.assetKey()));
        Map<AssetIdentity, AvailabilitySnapshot> result = new LinkedHashMap<>();
        jdbcTemplate.query(
            "select asset_type, asset_key, status, availability_epoch, source_sequence, event_id, payload_hash, reason " +
            "from catalog_asset_availability where asset_type = ? and asset_key in (" + placeholders + ")",
            row -> {
                AssetIdentity asset = new AssetIdentity(row.getString("asset_type"), row.getString("asset_key"));
                result.put(
                    asset,
                    new AvailabilitySnapshot(
                        row.getString("status"),
                        row.getLong("availability_epoch"),
                        row.getLong("source_sequence"),
                        row.getString("event_id"),
                        row.getString("payload_hash"),
                        row.getString("reason")
                    )
                );
            },
            arguments.toArray()
        );
        return result;
    }

    private void writeAvailabilityCurrent(
        AssetIdentity asset,
        String status,
        long epoch,
        long sourceSequence,
        UUID fenceId,
        String eventId,
        String payloadHash,
        String reason,
        Instant now
    ) {
        int changed = jdbcTemplate.update(
            """
            insert into catalog_asset_availability (
                asset_type, asset_key, status, availability_epoch, source_sequence,
                fence_id, event_id, payload_hash, reason, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (asset_type, asset_key) do update
               set status = excluded.status,
                   availability_epoch = excluded.availability_epoch,
                   source_sequence = excluded.source_sequence,
                   fence_id = excluded.fence_id,
                   event_id = excluded.event_id,
                   payload_hash = excluded.payload_hash,
                   reason = excluded.reason,
                   updated_at = excluded.updated_at
             where catalog_asset_availability.source_sequence < excluded.source_sequence
                or (
                    catalog_asset_availability.source_sequence = excluded.source_sequence
                    and catalog_asset_availability.event_id = excluded.event_id
                    and catalog_asset_availability.payload_hash = excluded.payload_hash
                )
            """,
            asset.assetType(),
            asset.assetKey(),
            status,
            epoch,
            sourceSequence,
            fenceId,
            eventId,
            payloadHash,
            truncate(reason, 512),
            Timestamp.from(now)
        );
        if (changed != 1) {
            throw RollbackInvalidationException.stale(
                "A newer availability event already owns asset " + asset.assetType() + ":" + asset.assetKey()
            );
        }
    }

    private void insertAvailabilityEvent(
        UUID receiptId,
        AssetIdentity asset,
        String status,
        long epoch,
        long sourceSequence,
        String eventId,
        String payloadHash,
        String reason,
        Instant now
    ) {
        int inserted = jdbcTemplate.update(
            """
            insert into catalog_asset_availability_event (
                id, event_id, payload_hash, receipt_id, asset_type, asset_key,
                status, availability_epoch, source_sequence, reason, occurred_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (event_id, asset_type, asset_key) do nothing
            """,
            UUID.randomUUID(),
            eventId,
            payloadHash,
            receiptId,
            asset.assetType(),
            asset.assetKey(),
            status,
            epoch,
            sourceSequence,
            truncate(reason, 512),
            Timestamp.from(now)
        );
        if (inserted == 1) {
            return;
        }
        String existingHash = jdbcTemplate.queryForObject(
            """
            select payload_hash from catalog_asset_availability_event
             where event_id = ? and asset_type = ? and asset_key = ?
            """,
            String.class,
            eventId,
            asset.assetType(),
            asset.assetKey()
        );
        if (!payloadHash.equals(existingHash)) {
            throw RollbackInvalidationException.conflict(
                "Availability event id already exists with a different payload: " + eventId
            );
        }
    }

    private void projectMappings(
        UUID receiptId,
        List<Target> targets,
        boolean restorePreviousEnabled,
        String eventId,
        long sourceSequence,
        Instant now,
        boolean clearFence
    ) {
        for (Target target : targets) {
            boolean enabled = restorePreviousEnabled && target.previousMappingEnabled();
            int changed = jdbcTemplate.update(
                """
                update infra_ods_table_mapping
                   set enabled = ?, availability_fence_id = ?, availability_event_id = ?,
                       availability_source_sequence = ?, last_modified_date = ?
                 where id = ? and availability_fence_id = ? and availability_source_sequence < ?
                """,
                enabled,
                clearFence ? null : receiptId,
                eventId,
                sourceSequence,
                Timestamp.from(now),
                target.odsMappingId(),
                receiptId,
                sourceSequence
            );
            if (changed != 1) {
                throw RollbackInvalidationException.stale(
                    "A newer ODS availability event already owns mapping " + target.odsMappingId()
                );
            }
        }
    }

    private AssetIdentity assetIdentity(UUID sourceDataSourceId, TargetSeed seed) {
        UUID sourceId = seed.datasetSourceId() == null ? sourceDataSourceId : seed.datasetSourceId();
        String schema = firstText(seed.hiveDatabase(), seed.odsSchema(), seed.streamNamespace(), "default");
        String table = firstText(seed.hiveTable(), seed.odsTable(), seed.streamName(), seed.datasetName());
        String key = CatalogAssetKey.dataset(sourceId, schema, schema, table, seed.datasetName());
        return new AssetIdentity(CatalogAssetType.DATASET.name(), key);
    }

    private boolean matches(TargetSeed seed, Set<String> filters) {
        if (filters.isEmpty()) {
            return true;
        }
        return candidates(seed).stream().anyMatch(filters::contains);
    }

    private List<String> candidates(TargetSeed seed) {
        return List.of(
            normalize(seed.streamName()),
            normalize(seed.odsTable()),
            qualified(seed.streamNamespace(), seed.streamName()),
            qualified(seed.odsSchema(), seed.odsTable())
        );
    }

    private Set<String> normalizeTables(List<String> tables) {
        if (tables == null || tables.isEmpty()) {
            return Set.of();
        }
        return tables.stream().filter(StringUtils::hasText).map(this::normalize).collect(java.util.stream.Collectors.toSet());
    }

    private String qualified(String namespace, String table) {
        return StringUtils.hasText(namespace) && StringUtils.hasText(table)
            ? normalize(namespace) + "." + normalize(table)
            : "";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private Set<AssetIdentity> uniqueAssets(List<Target> targets) {
        return targets
            .stream()
            .map(target -> new AssetIdentity(target.assetType(), target.assetKey()))
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private long targetPreviousEpoch(List<Target> targets, AssetIdentity asset) {
        return targets
            .stream()
            .filter(target -> target.assetType().equals(asset.assetType()) && target.assetKey().equals(asset.assetKey()))
            .mapToLong(target -> target.previousAvailability().epoch())
            .max()
            .orElse(0L);
    }

    private void requireOne(int changed, String message) {
        if (changed != 1) {
            throw RollbackInvalidationException.stale(message);
        }
    }

    private DispatchRecord mapDispatch(java.sql.ResultSet row) throws java.sql.SQLException {
        return new DispatchRecord(
            row.getObject("id", UUID.class),
            row.getObject("receipt_id", UUID.class),
            row.getString("command_hash"),
            row.getString("command_json"),
            row.getString("status"),
            row.getInt("dispatch_attempts"),
            row.getInt("generation_attempts"),
            instant(row.getTimestamp("claimed_at")),
            instant(row.getTimestamp("next_attempt_at")),
            instant(row.getTimestamp("sent_at")),
            instant(row.getTimestamp("completed_at")),
            row.getString("last_error")
        );
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private void requireClaimArguments(Instant now, Duration staleClaimTtl) {
        if (now == null) {
            throw new IllegalArgumentException("now is required");
        }
        if (staleClaimTtl == null || staleClaimTtl.isZero() || staleClaimTtl.isNegative()) {
            throw new IllegalArgumentException("staleClaimTtl must be positive");
        }
    }

    private void requireDispatchTransition(int changed, UUID receiptId, String message) {
        if (changed == 1 || hasDispatchStatus(receiptId, "COMPLETED")) {
            return;
        }
        throw RollbackInvalidationException.stale(message);
    }

    private boolean hasDispatchStatus(UUID receiptId, String status) {
        Boolean present = jdbcTemplate.queryForObject(
            """
            select exists (
                select 1 from integration_rollback_dispatch_outbox
                 where receipt_id = ? and status = ?
            )
            """,
            Boolean.class,
            receiptId,
            status
        );
        return Boolean.TRUE.equals(present);
    }

    private void acquireSourceSequenceLock() {
        jdbcTemplate.query(
            "select pg_advisory_xact_lock(?)",
            resultSet -> null,
            SOURCE_SEQUENCE_LOCK_ID
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static final String RECEIPT_COLUMNS =
        "id, idempotency_key, payload_hash, state, rollback_level, rollback_scope, task_id, " +
        "source_data_source_id, source_sequence, actor, completion_event_id, " +
        "completion_payload_hash, completion_source_sequence";

    public record Receipt(
        UUID id,
        String idempotencyKey,
        String payloadHash,
        String state,
        int level,
        String scope,
        Long taskId,
        UUID sourceDataSourceId,
        long sourceSequence,
        String actor,
        String completionEventId,
        String completionPayloadHash,
        Long completionSourceSequence
    ) {}

    public record AvailabilitySnapshot(
        String status,
        long epoch,
        long sourceSequence,
        String eventId,
        String payloadHash,
        String reason
    ) {
        public static AvailabilitySnapshot legacyAvailable() {
            return new AvailabilitySnapshot(AVAILABLE, 0L, 0L, null, null, null);
        }
    }

    public record Target(
        UUID id,
        UUID receiptId,
        String assetType,
        String assetKey,
        UUID odsMappingId,
        UUID datasetId,
        boolean previousMappingEnabled,
        AvailabilitySnapshot previousAvailability
    ) {}

    public record CompletionEvent(
        UUID receiptId,
        String eventId,
        String payloadHash,
        String outcome,
        String resultingState,
        long sourceSequence,
        String reason,
        boolean zeroSideEffectsConfirmed,
        String downstreamReference,
        Instant completedAt
    ) {}

    public record DispatchRecord(
        UUID id,
        UUID receiptId,
        String commandHash,
        String commandJson,
        String status,
        int attempts,
        int generationAttempts,
        Instant claimedAt,
        Instant nextAttemptAt,
        Instant sentAt,
        Instant completedAt,
        String lastError
    ) {}

    private record TargetSeed(
        UUID odsMappingId,
        UUID datasetId,
        boolean mappingEnabled,
        String streamName,
        String streamNamespace,
        String odsSchema,
        String odsTable,
        UUID datasetSourceId,
        String hiveDatabase,
        String hiveTable,
        String datasetName
    ) {}

    private record AssetIdentity(String assetType, String assetKey) {}
}
