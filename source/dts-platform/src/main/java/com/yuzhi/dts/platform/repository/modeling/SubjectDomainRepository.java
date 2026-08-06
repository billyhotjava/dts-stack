package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.service.modeling.SubjectDomainContract.CreateCommand;
import com.yuzhi.dts.platform.service.modeling.SubjectDomainContract.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.SubjectDomainContract.Status;
import com.yuzhi.dts.platform.service.modeling.SubjectDomainContract.View;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC persistence for the tenant-scoped subject-domain planning ledger. */
@Repository
public class SubjectDomainRepository {

    private static final String HEAD_COLUMNS = """
        select id, tenant_id, code, name, purpose, mart_id, status, revision, current_checksum,
               idempotency_key, idempotency_request_hash, created_date, last_modified_date
          from modeling_subject_domain
        """;

    private final JdbcTemplate jdbcTemplate;

    public SubjectDomainRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<StoredSubjectDomain> find(String tenantId, UUID id) {
        return jdbcTemplate
            .query(HEAD_COLUMNS + " where tenant_id = ? and id = ?", (rs, rowNum) -> map(rs, rowNum), tenantId, id)
            .stream()
            .findFirst();
    }

    public Optional<StoredSubjectDomain> findByIdempotencyKey(String tenantId, String key) {
        return jdbcTemplate
            .query(
                HEAD_COLUMNS + " where tenant_id = ? and idempotency_key = ?",
                (rs, rowNum) -> map(rs, rowNum),
                tenantId,
                key
            )
            .stream()
            .findFirst();
    }

    public List<StoredSubjectDomain> list(
        String tenantId,
        UUID martId,
        Status status,
        String keyword,
        int offset,
        int limit
    ) {
        StringBuilder sql = new StringBuilder(HEAD_COLUMNS);
        List<Object> parameters = new ArrayList<>();
        sql.append(" where tenant_id = ?");
        parameters.add(tenantId);
        if (martId != null) {
            sql.append(" and mart_id = ?");
            parameters.add(martId);
        }
        if (status != null) {
            sql.append(" and status = ?");
            parameters.add(status.name());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" and (lower(name) like ? or lower(code) like ? or lower(purpose) like ?)");
            String pattern = "%" + keyword.trim().toLowerCase(java.util.Locale.ROOT) + "%";
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
        }
        sql.append(" order by name, code offset ? limit ?");
        parameters.add(offset);
        parameters.add(limit);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> map(rs, rowNum), parameters.toArray());
    }

    /** The mart must exist and must not be retired before a subject domain can attach to it. */
    public boolean activeMartExists(String tenantId, UUID martId) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from modeling_data_mart where tenant_id = ? and id = ? and status <> 'RETIRED'",
            Integer.class,
            tenantId,
            martId
        );
        return count != null && count > 0;
    }

    public boolean activeNameExists(String tenantId, String name, UUID excludingId) {
        Integer count = jdbcTemplate.queryForObject(
            """
            select count(*)
              from modeling_subject_domain
             where tenant_id = ?
               and lower(name) = lower(?)
               and status <> 'RETIRED'
               and (?::uuid is null or id <> ?::uuid)
            """,
            Integer.class,
            tenantId,
            name,
            excludingId,
            excludingId
        );
        return count != null && count > 0;
    }

    public int insert(String tenantId, String actorId, CreateCommand command, View view, String requestHash) {
        return jdbcTemplate.update(
            """
            insert into modeling_subject_domain (
                id, tenant_id, code, name, purpose, mart_id, status, revision,
                current_checksum, idempotency_key, idempotency_request_hash,
                created_by, last_modified_by, created_date, last_modified_date
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (tenant_id, idempotency_key) do nothing
            """,
            view.id(),
            tenantId,
            view.code(),
            view.name(),
            view.purpose(),
            view.martId(),
            view.status().name(),
            view.revision(),
            view.checksum(),
            command.idempotencyKey(),
            requestHash,
            actorId,
            actorId,
            Timestamp.from(view.createdAt()),
            Timestamp.from(view.updatedAt())
        );
    }

    public int compareAndSet(String tenantId, String actorId, ExpectedVersion expected, View replacement) {
        return jdbcTemplate.update(
            """
            update modeling_subject_domain
               set name = ?, purpose = ?, mart_id = ?, status = ?, revision = ?,
                   current_checksum = ?, last_modified_by = ?, last_modified_date = ?
             where tenant_id = ? and id = ? and revision = ? and current_checksum = ?
            """,
            replacement.name(),
            replacement.purpose(),
            replacement.martId(),
            replacement.status().name(),
            replacement.revision(),
            replacement.checksum(),
            actorId,
            Timestamp.from(replacement.updatedAt()),
            tenantId,
            expected.id(),
            expected.revision(),
            expected.checksum()
        );
    }

    private static StoredSubjectDomain map(ResultSet row, int rowNumber) throws SQLException {
        return new StoredSubjectDomain(
            row.getObject("id", UUID.class),
            row.getString("code"),
            row.getString("name"),
            row.getString("purpose"),
            row.getObject("mart_id", UUID.class),
            Status.valueOf(row.getString("status")),
            row.getInt("revision"),
            row.getString("current_checksum"),
            row.getString("idempotency_key"),
            row.getString("idempotency_request_hash"),
            instant(row.getTimestamp("created_date")),
            instant(row.getTimestamp("last_modified_date"))
        );
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record StoredSubjectDomain(
        UUID id,
        String code,
        String name,
        String purpose,
        UUID martId,
        Status status,
        int revision,
        String checksum,
        String idempotencyKey,
        String idempotencyRequestHash,
        Instant createdAt,
        Instant updatedAt
    ) {}
}
