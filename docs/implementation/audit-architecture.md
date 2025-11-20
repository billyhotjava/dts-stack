# Audit Architecture Design

## Goals
- Capture every administrative and business API invocation as well as import/export activities across `dts-admin` and `dts-platform`.
- Persist audit events for at least 180 days in PostgreSQL with encryption-at-rest and tamper-evident metadata.
- Provide low-latency ingestion (~50k events/day) using asynchronous buffering while offering a retry path for forwarding events to the shared `dts-common` endpoint.
- Expose paginated, filterable APIs for the management UI with role-aware scoping and CSV export.

## Logical Components

| Component | Responsibility |
|-----------|----------------|
| `AuditEvent` domain | Immutable record containing actor, action, module, resource, client metadata, payload hash, signature chain, timestamps. |
| Audit Collector (admin/platform) | Interceptors and explicit hooks that create audit envelopes for each API call / domain action. |
| Async Audit Writer | Bounded queue + worker thread persisting events to PostgreSQL and invoking `dts-common` upload with retry/backoff. |
| Audit Repository | Batched insert/select/export queries with pagination & sorting; enforces retention and optional archival hooks. |
| REST Controller (`/api/audit-logs`) | Provides list/export endpoints, integrates role filtering rules (sysadmin/authadmin/auditadmin). |
| Front-end Audit Center | Fetches paginated data, supports real-time refresh, CSV export, and reflects module taxonomy. |

## Data Model

```sql
CREATE TABLE audit_event (
    id              BIGSERIAL PRIMARY KEY,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor           VARCHAR(128) NOT NULL,
    actor_role      VARCHAR(64),
    module          VARCHAR(64) NOT NULL,
    action          VARCHAR(128) NOT NULL,
    resource_type   VARCHAR(64),
    resource_id     VARCHAR(256),
    client_ip       INET,
    client_agent    VARCHAR(256),
    request_uri     TEXT,
    http_method     VARCHAR(16),
    result          VARCHAR(32) NOT NULL,
    latency_ms      INTEGER,
    payload_cipher  BYTEA,
    payload_hmac    VARCHAR(128) NOT NULL,
    chain_signature VARCHAR(128) NOT NULL,
    extra_tags      JSONB,
    created_by      VARCHAR(128) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_event_module_time ON audit_event (module, occurred_at DESC);
CREATE INDEX idx_audit_event_actor_time ON audit_event (actor, occurred_at DESC);
CREATE INDEX idx_audit_event_result_time ON audit_event (result, occurred_at DESC);
```

- `payload_cipher` stores encrypted JSON payload (AES-256-GCM) of contextual attributes (request body snapshot, response code, metadata). Encryption key will be supplied via `AUDIT_ENCRYPTION_KEY` secret in `.env`.
- `payload_hmac` uses HMAC-SHA256 over the plaintext to support forensic validation.
- `chain_signature` = HMAC-SHA256(previous chain + current payload_hmac) to detect deletion/tampering.

## Ingestion Flow

1. **Request Interceptor** (Spring `OncePerRequestFilter`)
   - Runs in both `dts-admin` and `dts-platform`.
   - Captures actor (from security context), module/action heuristics (based on handler metadata), client IP/UA, and marks start time.
   - On completion, compute latency, result (SUCCESS/FAILURE), collect tags (tenant, approval ID). delegate to `AuditRecorder`.
2. **Explicit Hooks** for asynchronous jobs (e.g., import/export) or when interception unavailable.
3. **AuditRecorder** enqueues a `PendingAuditEvent` (plain POJO) into a bounded `LinkedBlockingQueue` (size configurable, default 5000). If queue full, fallback to synchronous save to avoid data loss and emit WARN.
4. **AuditWorker** consumes queue, encrypts payload, computes HMAC & chain signature (with last persisted signature cached), inserts batch via repository, then attempts to POST to `dts-common` endpoint. On failure, event is pushed to an `RetryBuffer` (persistent or in-memory with scheduled retry). Retention job purges records >180 days nightly.

## API Surface (`/api/audit-logs`)
- `GET /api/audit-logs`: Parameters `page`, `size`, `sort`, `actor`, `module`, `action`, `result`, `resource`, `from`, `to`, `clientIp`. Returns paged data with decrypted subset (`payload_cipher` never exposed directly; decode selected keys).
- `GET /api/audit-logs/export`: Accepts same filters, streams CSV.
- Security: only authenticated; role filters applied server-side:
  - sysadmin: full access.
  - authadmin: sysadmin + auditadmin records.
  - auditadmin: sysadmin + authadmin + business users.

## Front-end Changes
- Replace mock data with API-backed table using `AuditLogService.getAuditLogs`.
- Implement filter form aligned with back-end parameters.
- Add “刷新” (poll) button calling latest page.
- Implement CSV export button hitting `/api/audit-logs/export` with current filters.
- Honor column taxonomy (module, action, result, actor, IP,时间). Display friendly operator labels using locale map.

## Configuration
- New env keys: `AUDIT_ENCRYPTION_KEY`, `AUDIT_QUEUE_CAPACITY`, `AUDIT_COMMON_BASE_URL`, `AUDIT_COMMON_TOKEN`.
- Retention job cron: `0 30 2 * * *` purge >180 days.
- Optional toggles: `AUDIT_FORWARD_ENABLED`, `AUDIT_FORWARD_RETRY_INTERVAL`.

## Outstanding Questions
- Confirm encryption key rotation strategy (manual update + re-encrypt offline?).
- Decide whether to mirror audit schema in `dts-platform` or use shared schema via cross-schema insert (initial approach: each service uses own schema but identical table + forwarder to admin). 

