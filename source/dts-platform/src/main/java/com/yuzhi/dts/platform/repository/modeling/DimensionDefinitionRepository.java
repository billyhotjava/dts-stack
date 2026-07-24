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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
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

    /**
     * Reads and locks the mutable definition head for the caller's transaction.
     *
     * <p>The immutable revision row remains unlocked; every revision advance or retirement updates the head first,
     * so a shared lock on {@code d} keeps the validated head stable until the surrounding ModelSpec create commits.
     */
    public Optional<StoredDimensionDefinition> findCurrentForShare(String tenantId, UUID id) {
        return jdbcTemplate
            .query(
                CURRENT_COLUMNS + " where d.tenant_id = ? and d.id = ? for share of d",
                this::mapStored,
                tenantId,
                id
            )
            .stream()
            .findFirst();
    }

    public List<StoredDimensionDefinition> listCurrent(
        String tenantId,
        UUID domainId,
        Status status,
        Set<UUID> visibleDomainIds,
        int offset,
        int limit
    ) {
        if (visibleDomainIds.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(CURRENT_COLUMNS).append(" where d.tenant_id = ?");
        ArrayList<Object> arguments = new ArrayList<>();
        arguments.add(tenantId);
        List<UUID> orderedVisibleDomainIds = visibleDomainIds.stream().sorted().toList();
        sql
            .append(" and d.domain_id in (")
            .append(String.join(", ", java.util.Collections.nCopies(orderedVisibleDomainIds.size(), "?")))
            .append(")");
        arguments.addAll(orderedVisibleDomainIds);
        if (domainId != null) {
            sql.append(" and d.domain_id = ?");
            arguments.add(domainId);
        }
        if (status != null) {
            sql.append(" and d.status = ?");
            arguments.add(status.name());
        }
        sql.append(" order by d.name, d.id limit ? offset ?");
        arguments.add(limit);
        arguments.add(offset);
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

    /**
     * Resolves the immutable definition revision projected for a legacy DIMENSION ModelSpec.
     *
     * <p>The domain predicate prevents a corrupt or manually inserted cross-domain map from becoming
     * an effective ModelSpec reference.
     */
    public Optional<LegacyDefinitionRef> findLegacyDefinitionRef(
        String tenantId,
        UUID legacyModelSpecId,
        UUID domainId
    ) {
        return jdbcTemplate
            .query(
                """
                select m.dimension_definition_id, m.dimension_definition_revision
                  from modeling_dimension_definition_legacy_map m
                  join modeling_dimension_definition d
                    on d.tenant_id = m.tenant_id
                   and d.id = m.dimension_definition_id
                 where m.tenant_id = ?
                   and m.legacy_model_spec_id = ?
                   and d.domain_id = ?
                """,
                (row, rowNumber) ->
                    new LegacyDefinitionRef(
                        row.getObject("dimension_definition_id", UUID.class),
                        row.getInt("dimension_definition_revision")
                    ),
                tenantId,
                legacyModelSpecId,
                domainId
            )
            .stream()
            .findFirst();
    }

    public boolean existsByDomainAndName(
        String tenantId,
        UUID domainId,
        String name,
        UUID excludingDefinitionId
    ) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
            select exists (
                select 1
                  from modeling_dimension_definition
                 where tenant_id = ?
                   and domain_id = ?
                   and lower(name) = lower(?)
                   and (?::uuid is null or id <> ?::uuid)
            )
            """,
            Boolean.class,
            tenantId,
            domainId,
            name,
            excludingDefinitionId,
            excludingDefinitionId
        );
        return Boolean.TRUE.equals(exists);
    }

    public int insert(String tenantId, String actorId, CreateCommand command, View view, String requestHash) {
        if (view.revision() != 1) {
            throw new IllegalArgumentException("Initial dimension definition revision must be 1");
        }

        String snapshot = json(view);
        Integer inserted = jdbcTemplate.queryForObject(
            """
            with inserted_head as (
                insert into modeling_dimension_definition (
                    id, tenant_id, system_code, domain_id, name, definition, owner_id, reuse_scope,
                    hierarchies_json, status, revision, current_checksum, idempotency_key,
                    idempotency_request_hash, idempotency_response_snapshot, created_by, last_modified_by,
                    created_date, last_modified_date
                ) values (
                    ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, 1, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?
                )
                on conflict (tenant_id, idempotency_key) do nothing
                returning tenant_id, id
            ),
            inserted_revision as (
                insert into modeling_dimension_definition_revision (
                    id, tenant_id, dimension_definition_id, revision, system_code, domain_id, name,
                    definition, owner_id, reuse_scope, hierarchies_json, status, content_checksum,
                    snapshot_json, created_by, created_date
                )
                select ?, inserted_head.tenant_id, inserted_head.id, 1, ?, ?, ?, ?, ?, ?,
                       cast(? as jsonb), ?, ?, cast(? as jsonb), ?, ?
                  from inserted_head
                returning 1
            )
            select count(*)::int from inserted_revision
            """,
            Integer.class,
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
            view.checksum(),
            command.idempotencyKey(),
            requestHash,
            snapshot,
            actorId,
            actorId,
            Timestamp.from(view.createdAt()),
            Timestamp.from(view.updatedAt()),
            UUID.randomUUID(),
            view.systemCode(),
            view.domainId(),
            view.name(),
            view.definition(),
            view.ownerId(),
            view.reuseScope().name(),
            json(view.hierarchies()),
            view.status().name(),
            view.checksum(),
            snapshot,
            actorId,
            Timestamp.from(view.updatedAt())
        );
        if (inserted == null || inserted == 0) {
            String committedRequestHash = jdbcTemplate.queryForObject(
                """
                select idempotency_request_hash
                  from modeling_dimension_definition
                 where tenant_id = ? and idempotency_key = ?
                """,
                String.class,
                tenantId,
                command.idempotencyKey()
            );
            if (requestHash.equals(committedRequestHash)) {
                return 0;
            }
            throw new DataIntegrityViolationException(
                "Dimension definition idempotency key was already used with a different request hash"
            );
        }
        return inserted;
    }

    public int compareAndSet(String tenantId, String actorId, ExpectedVersion expected, View replacement) {
        if (!replacement.id().equals(expected.id())) {
            throw new IllegalArgumentException("Replacement dimension definition ID must match the expected ID");
        }
        if (replacement.revision() != expected.revision() + 1) {
            throw new IllegalArgumentException("Replacement revision must immediately follow the expected revision");
        }

        Integer updated = jdbcTemplate.queryForObject(
            """
            with updated_head as (
                update modeling_dimension_definition
                   set name = ?, definition = ?, owner_id = ?, reuse_scope = ?, hierarchies_json = cast(? as jsonb),
                       status = ?, revision = ?, current_checksum = ?, last_modified_by = ?, last_modified_date = ?
                 where tenant_id = ? and id = ? and revision = ? and current_checksum = ?
                   and system_code = ? and domain_id = ? and created_date = ?
                returning tenant_id, id
            ),
            inserted_revision as (
                insert into modeling_dimension_definition_revision (
                    id, tenant_id, dimension_definition_id, revision, system_code, domain_id, name,
                    definition, owner_id, reuse_scope, hierarchies_json, status, content_checksum,
                    snapshot_json, created_by, created_date
                )
                select ?, updated_head.tenant_id, updated_head.id, ?, ?, ?, ?, ?, ?, ?,
                       cast(? as jsonb), ?, ?, cast(? as jsonb), ?, ?
                  from updated_head
                returning 1
            )
            select count(*)::int from inserted_revision
            """,
            Integer.class,
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
            expected.checksum(),
            replacement.systemCode(),
            replacement.domainId(),
            Timestamp.from(replacement.createdAt()),
            UUID.randomUUID(),
            replacement.revision(),
            replacement.systemCode(),
            replacement.domainId(),
            replacement.name(),
            replacement.definition(),
            replacement.ownerId(),
            replacement.reuseScope().name(),
            json(replacement.hierarchies()),
            replacement.status().name(),
            replacement.checksum(),
            json(replacement),
            actorId,
            Timestamp.from(replacement.updatedAt())
        );
        return updated == null ? 0 : updated;
    }

    public long usageCount(String tenantId, UUID id) {
        Long count = jdbcTemplate.queryForObject(
            """
            select count(distinct usage.model_spec_id)
              from (
                    select s.id as model_spec_id
                      from modeling_model_spec s
                     where s.tenant_id = ? and s.dimension_definition_id = ?
                    union all
                    select m.legacy_model_spec_id as model_spec_id
                      from modeling_dimension_definition_legacy_map m
                     where m.tenant_id = ? and m.dimension_definition_id = ?
                   ) usage
            """,
            Long.class,
            tenantId,
            id,
            tenantId,
            id
        );
        return count == null ? 0 : count;
    }

    public Map<UUID, Long> usageCounts(String tenantId, List<UUID> ids) {
        List<UUID> distinctIds = ids.stream().distinct().toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(distinctIds.size(), "?"));
        ArrayList<Object> arguments = new ArrayList<>(distinctIds.size() + 1);
        arguments.add(tenantId);
        arguments.addAll(distinctIds);
        arguments.add(tenantId);
        arguments.addAll(distinctIds);
        return jdbcTemplate.query(
            """
            select usage.dimension_definition_id, count(distinct usage.model_spec_id) as usage_count
              from (
                    select s.dimension_definition_id, s.id as model_spec_id
                      from modeling_model_spec s
                     where s.tenant_id = ?
                       and s.dimension_definition_id in (%s)
                    union all
                    select m.dimension_definition_id, m.legacy_model_spec_id as model_spec_id
                      from modeling_dimension_definition_legacy_map m
                     where m.tenant_id = ?
                       and m.dimension_definition_id in (%s)
                   ) usage
             group by usage.dimension_definition_id
            """.formatted(placeholders, placeholders),
            resultSet -> {
                Map<UUID, Long> counts = new LinkedHashMap<>();
                while (resultSet.next()) {
                    counts.put(
                        resultSet.getObject("dimension_definition_id", UUID.class),
                        resultSet.getLong("usage_count")
                    );
                }
                return Map.copyOf(counts);
            },
            arguments.toArray()
        );
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

    public record LegacyDefinitionRef(UUID dimensionDefinitionId, int revision) {}

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
