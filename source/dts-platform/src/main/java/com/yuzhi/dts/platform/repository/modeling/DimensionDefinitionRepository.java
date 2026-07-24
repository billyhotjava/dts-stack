package com.yuzhi.dts.platform.repository.modeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.CreateCommand;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.HierarchySemantic;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.ReuseScope;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.Status;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.View;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL persistence adapter for reusable dimension definition heads and immutable revisions. */
@Repository
public class DimensionDefinitionRepository {

    private static final TypeReference<List<HierarchySemantic>> HIERARCHY_LIST = new TypeReference<>() {};

    private static final String CURRENT_COLUMNS = """
        select true as current_head, d.tenant_id, d.id, d.system_code, d.domain_id, d.name, d.definition,
               d.owner_id, d.reuse_scope, d.hierarchies_json::text as hierarchies_json, d.status, d.revision,
               d.current_checksum, r.content_checksum as revision_checksum, r.snapshot_json::text as current_snapshot,
               d.idempotency_key, d.idempotency_request_hash,
               d.idempotency_response_snapshot::text as idempotency_response_snapshot,
               d.created_date, d.last_modified_date
          from modeling_dimension_definition d
          left join modeling_dimension_definition_revision r
            on r.tenant_id = d.tenant_id
           and r.dimension_definition_id = d.id
           and r.revision = d.revision
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DimensionDefinitionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<StoredDimensionDefinition> findCurrent(String tenantId, UUID id) {
        return jdbcTemplate
            .query(
                CURRENT_COLUMNS + " where d.tenant_id = ? and d.id = ?",
                this::mapStored,
                tenantId,
                id
            )
            .stream()
            .findFirst();
    }

    public List<StoredDimensionDefinition> listCurrent(String tenantId, UUID domainId, Status status) {
        StringBuilder sql = new StringBuilder(CURRENT_COLUMNS).append(" where d.tenant_id = ?");
        java.util.ArrayList<Object> arguments = new java.util.ArrayList<>();
        arguments.add(tenantId);
        if (domainId != null) {
            sql.append(" and d.domain_id = ?");
            arguments.add(domainId);
        }
        if (status != null) {
            sql.append(" and d.status = ?");
            arguments.add(status.name());
        }
        sql.append(" order by d.name, d.id");
        return jdbcTemplate.query(sql.toString(), this::mapStored, arguments.toArray());
    }

    public Optional<StoredDimensionDefinition> findByIdempotencyKey(String tenantId, String key) {
        return jdbcTemplate
            .query(
                CURRENT_COLUMNS + " where d.tenant_id = ? and d.idempotency_key = ?",
                this::mapStored,
                tenantId,
                key
            )
            .stream()
            .findFirst();
    }

    public Optional<StoredDimensionDefinition> findRevision(String tenantId, UUID id, int revision) {
        return jdbcTemplate
            .query(
                """
                select false as current_head, r.tenant_id, r.dimension_definition_id as id, r.system_code,
                       r.domain_id, r.name, r.definition, r.owner_id, r.reuse_scope,
                       r.hierarchies_json::text as hierarchies_json, r.status, r.revision,
                       null as current_checksum, r.content_checksum as revision_checksum,
                       r.snapshot_json::text as current_snapshot, d.idempotency_key, d.idempotency_request_hash,
                       d.idempotency_response_snapshot::text as idempotency_response_snapshot,
                       d.created_date, r.created_date as last_modified_date
                  from modeling_dimension_definition_revision r
                  join modeling_dimension_definition d
                    on d.tenant_id = r.tenant_id and d.id = r.dimension_definition_id
                 where r.tenant_id = ? and r.dimension_definition_id = ? and r.revision = ?
                """,
                this::mapStored,
                tenantId,
                id,
                revision
            )
            .stream()
            .findFirst();
    }

    public int insert(String tenantId, String actorId, CreateCommand command, View view, String requestHash) {
        String snapshot = json(view);
        return jdbcTemplate.update(
            """
            insert into modeling_dimension_definition (
                id, tenant_id, system_code, domain_id, name, definition, owner_id, reuse_scope,
                hierarchies_json, status, revision, current_checksum, idempotency_key,
                idempotency_request_hash, idempotency_response_snapshot, created_by, last_modified_by,
                created_date, last_modified_date
            ) values (
                ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?
            )
            on conflict (tenant_id, idempotency_key) do nothing
            """,
            view.id(),
            tenantId,
            view.systemCode(),
            view.domainId(),
            view.name(),
            view.definition(),
            view.ownerId(),
            view.reuseScope().name(),
            json(view.hierarchies()),
            view.status().name(),
            view.revision(),
            view.checksum(),
            command.idempotencyKey(),
            requestHash,
            snapshot,
            actorId,
            actorId,
            Timestamp.from(view.createdAt()),
            Timestamp.from(view.updatedAt())
        );
    }

    public int compareAndSet(String tenantId, String actorId, ExpectedVersion expected, View replacement) {
        return jdbcTemplate.update(
            """
            update modeling_dimension_definition
               set name = ?, definition = ?, owner_id = ?, reuse_scope = ?, hierarchies_json = cast(? as jsonb),
                   status = ?, revision = ?, current_checksum = ?, last_modified_by = ?, last_modified_date = ?
             where tenant_id = ? and id = ? and revision = ? and current_checksum = ?
            """,
            replacement.name(),
            replacement.definition(),
            replacement.ownerId(),
            replacement.reuseScope().name(),
            json(replacement.hierarchies()),
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

    public int appendRevision(String tenantId, String actorId, View view) {
        return jdbcTemplate.update(
            """
            insert into modeling_dimension_definition_revision (
                id, tenant_id, dimension_definition_id, revision, system_code, domain_id, name,
                definition, owner_id, reuse_scope, hierarchies_json, status, content_checksum,
                snapshot_json, created_by, created_date
            ) values (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, cast(? as jsonb), ?, ?
            )
            """,
            UUID.randomUUID(),
            tenantId,
            view.id(),
            view.revision(),
            view.systemCode(),
            view.domainId(),
            view.name(),
            view.definition(),
            view.ownerId(),
            view.reuseScope().name(),
            json(view.hierarchies()),
            view.status().name(),
            view.checksum(),
            json(view),
            actorId,
            Timestamp.from(view.updatedAt())
        );
    }

    public long usageCount(String tenantId, UUID id) {
        Long count = jdbcTemplate.queryForObject(
            """
            select count(*)
              from modeling_model_spec
             where tenant_id = ? and dimension_definition_id = ?
            """,
            Long.class,
            tenantId,
            id
        );
        return count == null ? 0 : count;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Dimension definition persistence payload cannot be serialized", exception);
        }
    }

    private StoredDimensionDefinition mapStored(ResultSet row, int rowNumber) throws SQLException {
        return new StoredDimensionDefinition(
            row.getBoolean("current_head"),
            row.getString("tenant_id"),
            row.getObject("id", UUID.class),
            row.getString("system_code"),
            row.getObject("domain_id", UUID.class),
            row.getString("name"),
            row.getString("definition"),
            row.getString("owner_id"),
            ReuseScope.valueOf(row.getString("reuse_scope")),
            hierarchies(row.getString("hierarchies_json")),
            Status.valueOf(row.getString("status")),
            row.getInt("revision"),
            row.getString("current_checksum"),
            row.getString("revision_checksum"),
            row.getString("current_snapshot"),
            row.getString("idempotency_key"),
            row.getString("idempotency_request_hash"),
            row.getString("idempotency_response_snapshot"),
            instant(row.getTimestamp("created_date")),
            instant(row.getTimestamp("last_modified_date"))
        );
    }

    private List<HierarchySemantic> hierarchies(String value) {
        try {
            return value == null ? List.of() : objectMapper.readValue(value, HIERARCHY_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Stored dimension hierarchy payload is invalid", exception);
        }
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }

    public record ExpectedVersion(UUID id, int revision, String checksum) {}

    public record StoredDimensionDefinition(
        boolean currentHead,
        String tenantId,
        UUID id,
        String systemCode,
        UUID domainId,
        String name,
        String definition,
        String ownerId,
        ReuseScope reuseScope,
        List<HierarchySemantic> hierarchies,
        Status status,
        int revision,
        String currentChecksum,
        String revisionChecksum,
        String currentSnapshot,
        String idempotencyKey,
        String idempotencyRequestHash,
        String idempotencyResponseSnapshot,
        Instant createdAt,
        Instant updatedAt
    ) {
        public String checksum() {
            return revisionChecksum != null ? revisionChecksum : currentChecksum;
        }

        public View toView(long usageCount) {
            return new View(
                id,
                systemCode,
                domainId,
                name,
                definition,
                ownerId,
                reuseScope,
                hierarchies,
                status,
                revision,
                checksum(),
                usageCount,
                createdAt,
                updatedAt
            );
        }
    }
}
