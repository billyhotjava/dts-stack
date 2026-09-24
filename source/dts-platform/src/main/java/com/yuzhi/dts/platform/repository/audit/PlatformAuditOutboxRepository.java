package com.yuzhi.dts.platform.repository.audit;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Durable delivery ledger for platform audit events. */
@Repository
public class PlatformAuditOutboxRepository {

    public static final String LEGACY_UNSCOPED_TENANT = "__legacy_unscoped__";

    private final JdbcTemplate jdbcTemplate;

    public PlatformAuditOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 审计写入必须独立于调用方事务：概览/检索等只读端点会在 readOnly 事务内记审计，
    // 若 enqueue 参与该事务，PG 会以 "cannot execute INSERT in a read-only transaction" 拒绝并导致端点 500。
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID enqueue(EnqueueCommand command) {
        return insert(command);
    }

    /** Strict write commands must commit their state and audit receipt in the same transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueueTransactional(EnqueueCommand command) {
        return insert(command);
    }

    private UUID insert(EnqueueCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String tenantId = requiredTenant(command.tenantId());
        int inserted = jdbcTemplate.update(
            """
            insert into platform_audit_outbox (
                id, tenant_id, event_id, producer, occurred_at, payload_hash, body_json,
                status, dispatch_attempts, next_attempt_at, created_at, last_modified_at
            ) values (?, ?, ?, 'dts-platform', ?, ?, ?, 'PENDING', 0, ?, ?, ?)
            on conflict (event_id) do nothing
            """,
            id,
            tenantId,
            required(command.eventId(), "eventId"),
            Timestamp.from(required(command.occurredAt(), "occurredAt")),
            required(command.payloadHash(), "payloadHash"),
            required(command.bodyJson(), "bodyJson"),
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(now)
        );
        if (inserted == 1) return id;
        ExistingEvent existing = jdbcTemplate.queryForObject(
            "select id, tenant_id, payload_hash from platform_audit_outbox where event_id = ?",
            (row, rowNumber) ->
                new ExistingEvent(
                    row.getObject("id", UUID.class),
                    row.getString("tenant_id"),
                    row.getString("payload_hash")
                ),
            command.eventId()
        );
        if (
            existing == null ||
            !tenantId.equals(existing.tenantId()) ||
            !command.payloadHash().equals(existing.payloadHash())
        ) {
            throw new IllegalStateException("Audit event id already exists with different payload or ownership");
        }
        return existing.id();
    }

    public Optional<ReplayTarget> findReplayTarget(String tenantId, UUID id) {
        String scopedTenant = requiredTenant(tenantId);
        if (id == null) throw new IllegalArgumentException("id is required");
        return jdbcTemplate
            .query(
                """
                select id, event_id, producer, payload_hash, body_json, status, dispatch_attempts
                  from platform_audit_outbox
                 where id = ? and tenant_id = ?
                """,
                (row, rowNumber) ->
                    new ReplayTarget(
                        row.getObject("id", UUID.class),
                        row.getString("event_id"),
                        row.getString("producer"),
                        row.getString("payload_hash"),
                        row.getString("body_json"),
                        row.getString("status"),
                        row.getInt("dispatch_attempts")
                    ),
                id,
                scopedTenant
            )
            .stream()
            .findFirst();
    }

    @Transactional
    public int replayDead(ReplayCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        String tenantId = requiredTenant(command.tenantId());
        UUID id = required(command.id(), "id");
        String payloadHash = required(command.expectedPayloadHash(), "expectedPayloadHash");
        String bodyJson = required(command.expectedBodyJson(), "expectedBodyJson");
        Instant nextAttemptAt = required(command.nextAttemptAt(), "nextAttemptAt");
        Instant now = required(command.now(), "now");
        return jdbcTemplate.update(
            """
            update platform_audit_outbox
               set status = 'PENDING', claimed_at = null, next_attempt_at = ?,
                   generation_attempts = 0, last_modified_at = ?
             where id = ? and tenant_id = ? and status = 'DEAD'
               and payload_hash = ? and body_json = ?
            """,
            Timestamp.from(nextAttemptAt),
            Timestamp.from(now),
            id,
            tenantId,
            payloadHash,
            bodyJson
        );
    }

    @Transactional
    public Optional<ClaimedAudit> claimNext(Instant now, Duration staleClaimTtl) {
        if (now == null) throw new IllegalArgumentException("now is required");
        if (staleClaimTtl == null || staleClaimTtl.isZero() || staleClaimTtl.isNegative()) {
            throw new IllegalArgumentException("staleClaimTtl must be positive");
        }
        Instant staleBefore = now.minus(staleClaimTtl);
        return jdbcTemplate
            .query(
                """
                with next_audit as (
                    select id
                      from platform_audit_outbox
                     where (
                            status in ('PENDING', 'RETRY')
                            and next_attempt_at <= ?
                         )
                        or (status = 'CLAIMED' and claimed_at < ?)
                     order by next_attempt_at, created_at, id
                     for update skip locked
                     limit 1
                )
                update platform_audit_outbox a
                   set status = 'CLAIMED', claimed_at = ?,
                       dispatch_attempts = dispatch_attempts + 1,
                       generation_attempts = generation_attempts + 1,
                       last_modified_at = ?
                  from next_audit n
                 where a.id = n.id
                returning a.id, a.event_id, a.producer, a.payload_hash, a.body_json,
                          a.dispatch_attempts, a.generation_attempts
                """,
                (row, rowNumber) ->
                    new ClaimedAudit(
                        row.getObject("id", UUID.class),
                        row.getString("event_id"),
                        row.getString("producer"),
                        row.getString("payload_hash"),
                        row.getString("body_json"),
                        row.getInt("dispatch_attempts"),
                        row.getInt("generation_attempts")
                    ),
                Timestamp.from(now),
                Timestamp.from(staleBefore),
                Timestamp.from(now),
                Timestamp.from(now)
            )
            .stream()
            .findFirst();
    }

    @Transactional
    public void markSent(UUID id, int claimAttempt, Instant now) {
        requireOne(
            jdbcTemplate.update(
                """
                update platform_audit_outbox
                   set status = 'SENT', claimed_at = null, next_attempt_at = null,
                       delivered_at = ?, last_error = null, last_modified_at = ?
                 where id = ? and status = 'CLAIMED' and dispatch_attempts = ?
                """,
                Timestamp.from(now),
                Timestamp.from(now),
                id,
                claimAttempt
            ),
            "Sent audit outbox row could not be persisted"
        );
    }

    @Transactional
    public void markRetry(UUID id, int claimAttempt, String error, Instant nextAttemptAt, Instant now) {
        requireOne(
            jdbcTemplate.update(
                """
                update platform_audit_outbox
                   set status = 'RETRY', claimed_at = null, next_attempt_at = ?,
                       last_error = ?, last_modified_at = ?
                 where id = ? and status = 'CLAIMED' and dispatch_attempts = ?
                """,
                Timestamp.from(nextAttemptAt),
                truncate(error, 2048),
                Timestamp.from(now),
                id,
                claimAttempt
            ),
            "Retry audit outbox row could not be persisted"
        );
    }

    @Transactional
    public void markDead(UUID id, int claimAttempt, String error, Instant now) {
        requireOne(
            jdbcTemplate.update(
                """
                update platform_audit_outbox
                   set status = 'DEAD', claimed_at = null, next_attempt_at = null,
                       last_error = ?, last_modified_at = ?
                 where id = ? and status = 'CLAIMED' and dispatch_attempts = ?
                """,
                truncate(error, 2048),
                Timestamp.from(now),
                id,
                claimAttempt
            ),
            "Dead audit outbox row could not be persisted"
        );
    }

    @Transactional
    public int purgeSentBefore(Instant cutoff, int limit) {
        if (cutoff == null) throw new IllegalArgumentException("cutoff is required");
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        return jdbcTemplate.update(
            """
            delete from platform_audit_outbox
             where id in (
                select id from platform_audit_outbox
                 where status = 'SENT' and delivered_at < ?
                 order by delivered_at, id
                 limit ?
             )
            """,
            Timestamp.from(cutoff),
            limit
        );
    }

    public long countPending() {
        Long count = jdbcTemplate.queryForObject(
            "select count(*) from platform_audit_outbox where status in ('PENDING', 'CLAIMED', 'RETRY')",
            Long.class
        );
        return count == null ? 0 : count;
    }

    public long countDead() {
        Long count = jdbcTemplate.queryForObject(
            "select count(*) from platform_audit_outbox where status = 'DEAD'",
            Long.class
        );
        return count == null ? 0 : count;
    }

    private void requireOne(int updated, String message) {
        if (updated != 1) throw new IllegalStateException(message);
    }

    private String truncate(String value, int maxLength) {
        String safe = value == null ? "unknown" : value;
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private String requiredTenant(String tenantId) {
        String value = required(tenantId, "tenantId").trim();
        if (value.length() > 128) throw new IllegalArgumentException("tenantId is too long");
        if (LEGACY_UNSCOPED_TENANT.equals(value)) {
            throw new IllegalArgumentException("legacy unscoped audit rows cannot be operated manually");
        }
        return value;
    }

    private <T> T required(T value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    public record EnqueueCommand(String tenantId, String eventId, Instant occurredAt, String payloadHash, String bodyJson) {}

    public record ClaimedAudit(
        UUID id,
        String eventId,
        String producer,
        String payloadHash,
        String bodyJson,
        int attempts,
        int generationAttempts
    ) {}

    public record ReplayTarget(
        UUID id,
        String eventId,
        String producer,
        String payloadHash,
        String bodyJson,
        String status,
        int attemptCount
    ) {}

    public record ReplayCommand(
        String tenantId,
        UUID id,
        String expectedPayloadHash,
        String expectedBodyJson,
        Instant nextAttemptAt,
        Instant now
    ) {}

    private record ExistingEvent(UUID id, String tenantId, String payloadHash) {}
}
