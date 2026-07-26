package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.service.modeling.DataMartContract.CreateCommand;
import com.yuzhi.dts.platform.service.modeling.DataMartContract.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.DataMartContract.Status;
import com.yuzhi.dts.platform.service.modeling.DataMartContract.View;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

@Repository
public class DataMartRepository {

    private static final String HEAD_COLUMNS = """
        select id, code, name, purpose, owner_id, status, revision, current_checksum,
               idempotency_key, idempotency_request_hash, created_date, last_modified_date
          from modeling_data_mart
        """;

    private final JdbcTemplate jdbcTemplate;

    public DataMartRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<StoredDataMart> find(String tenantId, UUID id) {
        return jdbcTemplate
            .query(HEAD_COLUMNS + " where tenant_id = ? and id = ?", this::map, tenantId, id)
            .stream()
            .findFirst();
    }

    public Optional<StoredDataMart> findByIdempotencyKey(String tenantId, String key) {
        return jdbcTemplate
            .query(HEAD_COLUMNS + " where tenant_id = ? and idempotency_key = ?", this::map, tenantId, key)
            .stream()
            .findFirst();
    }

    public List<StoredDataMart> list(
        String tenantId,
        UUID domainId,
        Status status,
        String keyword,
        int offset,
        int limit
    ) {
        StringBuilder sql = new StringBuilder(HEAD_COLUMNS);
        List<Object> parameters = new ArrayList<>();
        sql.append(" where tenant_id = ?");
        parameters.add(tenantId);
        if (status != null) {
            sql.append(" and status = ?");
            parameters.add(status.name());
        }
        if (domainId != null) {
            sql.append(
                " and exists (select 1 from modeling_data_mart_domain d where d.tenant_id = modeling_data_mart.tenant_id and d.data_mart_id = modeling_data_mart.id and d.domain_id = ?)"
            );
            parameters.add(domainId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(
                " and (lower(name) like ? or lower(code) like ? or lower(purpose) like ? or lower(owner_id) like ?)"
            );
            String pattern = "%" + keyword.trim().toLowerCase(java.util.Locale.ROOT) + "%";
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
        }
        sql.append(" order by name, id offset ? limit ?");
        parameters.add(offset);
        parameters.add(limit);
        return jdbcTemplate.query(sql.toString(), this::map, parameters.toArray());
    }

    public boolean domainsExist(List<UUID> domainIds) {
        if (domainIds.isEmpty()) {
            return false;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(domainIds.size(), "?"));
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from catalog_domain where id in (" + placeholders + ")",
            Integer.class,
            domainIds.toArray()
        );
        return count != null && count == domainIds.size();
    }

    public boolean activeNameExists(String tenantId, String name, UUID excludingId) {
        Integer count = jdbcTemplate.queryForObject(
            """
            select count(*)
              from modeling_data_mart
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

    public int insert(String tenantId, String actorId, CreateCommand command, View view, String requestHash, String snapshot) {
        int inserted = jdbcTemplate.update(
            """
            insert into modeling_data_mart (
                id, tenant_id, code, name, purpose, owner_id, status, revision,
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
            view.ownerId(),
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
        if (inserted == 0) {
            return 0;
        }
        replaceDomains(tenantId, actorId, view.id(), view.domainIds(), view.updatedAt());
        insertRevision(tenantId, actorId, view, snapshot);
        return 1;
    }

    public int compareAndSet(
        String tenantId,
        String actorId,
        ExpectedVersion expected,
        View replacement,
        String snapshot
    ) {
        int updated = jdbcTemplate.update(
            """
            update modeling_data_mart
               set name = ?, purpose = ?, owner_id = ?, status = ?, revision = ?,
                   current_checksum = ?, last_modified_by = ?, last_modified_date = ?
             where tenant_id = ? and id = ? and revision = ? and current_checksum = ?
            """,
            replacement.name(),
            replacement.purpose(),
            replacement.ownerId(),
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
        if (updated == 0) {
            return 0;
        }
        replaceDomains(tenantId, actorId, replacement.id(), replacement.domainIds(), replacement.updatedAt());
        insertRevision(tenantId, actorId, replacement, snapshot);
        return 1;
    }

    public Map<UUID, List<UUID>> domainIds(String tenantId, List<UUID> dataMartIds) {
        Map<UUID, List<UUID>> result = new LinkedHashMap<>();
        dataMartIds.forEach(id -> result.put(id, new ArrayList<>()));
        if (dataMartIds.isEmpty()) {
            return result;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(dataMartIds.size(), "?"));
        List<Object> parameters = new ArrayList<>();
        parameters.add(tenantId);
        parameters.addAll(dataMartIds);
        jdbcTemplate.query(
            """
            select data_mart_id, domain_id
              from modeling_data_mart_domain
             where tenant_id = ? and data_mart_id in (%s)
             order by data_mart_id, domain_id
            """.formatted(placeholders),
            (RowCallbackHandler) row ->
                result
                    .computeIfAbsent(row.getObject("data_mart_id", UUID.class), ignored -> new ArrayList<>())
                    .add(row.getObject("domain_id", UUID.class)),
            parameters.toArray()
        );
        return result;
    }

    public Map<UUID, Long> usageCounts(String tenantId, List<UUID> dataMartIds) {
        Map<UUID, Long> result = new LinkedHashMap<>();
        dataMartIds.forEach(id -> result.put(id, 0L));
        if (dataMartIds.isEmpty()) {
            return result;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(dataMartIds.size(), "?"));
        List<Object> parameters = new ArrayList<>();
        parameters.add(tenantId);
        parameters.addAll(dataMartIds);
        parameters.add(tenantId);
        parameters.addAll(dataMartIds);
        parameters.add(tenantId);
        parameters.addAll(dataMartIds);
        jdbcTemplate.query(
            """
            select data_mart_id, count(distinct usage_ref) as usage_count
              from (
                    select data_mart_id, concat('PLAN:', plan_id::text) as usage_ref
                      from modeling_warehouse_plan_data_mart
                     where tenant_id = ? and data_mart_id in (%s)
                    union all
                    select data_mart_id, concat('DIMENSION:', id::text) as usage_ref
                      from modeling_dimension_definition
                     where tenant_id = ? and data_mart_id in (%s) and status <> 'RETIRED'
                    union all
                    select data_mart_id, concat('MODEL:', id::text) as usage_ref
                      from modeling_model_spec
                     where tenant_id = ? and data_mart_id in (%s) and status <> 'ARCHIVED'
                   ) usage
             group by data_mart_id
            """.formatted(placeholders, placeholders, placeholders),
            (RowCallbackHandler) row ->
                result.put(row.getObject("data_mart_id", UUID.class), row.getLong("usage_count")),
            parameters.toArray()
        );
        return result;
    }

    public PlanBinding readPlanBinding(String tenantId, UUID planId) {
        List<Integer> versions = jdbcTemplate.query(
            "select data_marts_version from modeling_warehouse_plan where tenant_id = ? and id = ?",
            (row, index) -> row.getInt(1),
            tenantId,
            planId
        );
        if (versions.isEmpty()) {
            return null;
        }
        List<UUID> ids = jdbcTemplate.query(
            """
            select data_mart_id
              from modeling_warehouse_plan_data_mart
             where tenant_id = ? and plan_id = ?
             order by data_mart_id
            """,
            (row, index) -> row.getObject(1, UUID.class),
            tenantId,
            planId
        );
        return new PlanBinding(planId, ids, versions.getFirst());
    }

    public boolean allCurrentForPlan(String tenantId, UUID planId, List<UUID> dataMartIds) {
        if (dataMartIds.isEmpty()) {
            return true;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(dataMartIds.size(), "?"));
        List<Object> parameters = new ArrayList<>();
        parameters.add(tenantId);
        parameters.addAll(dataMartIds);
        Integer count = jdbcTemplate.queryForObject(
            """
            select count(*)
              from modeling_data_mart mart
             where mart.tenant_id = ?
               and mart.status = 'CURRENT'
               and mart.id in (%s)
               and not exists (
                    select 1
                      from modeling_data_mart_domain mart_domain
                     where mart_domain.tenant_id = mart.tenant_id
                       and mart_domain.data_mart_id = mart.id
                       and not exists (
                            select 1
                              from modeling_warehouse_plan_domain plan_domain
                             where plan_domain.tenant_id = mart.tenant_id
                               and plan_domain.plan_id = ?
                               and plan_domain.domain_id = mart_domain.domain_id
                               and plan_domain.confirmation_status = 'CONFIRMED'
                       )
               )
            """.formatted(placeholders),
            Integer.class,
            append(parameters, planId)
        );
        return count != null && count == dataMartIds.size();
    }

    private static Object[] append(List<Object> values, Object value) {
        List<Object> result = new ArrayList<>(values);
        result.add(value);
        return result.toArray();
    }

    public int replacePlanBinding(
        String tenantId,
        String actorId,
        UUID planId,
        int expectedVersion,
        List<UUID> dataMartIds,
        Instant now
    ) {
        int updated = jdbcTemplate.update(
            """
            update modeling_warehouse_plan
               set data_marts_version = data_marts_version + 1,
                   last_modified_date = ?
             where tenant_id = ? and id = ? and data_marts_version = ?
            """,
            Timestamp.from(now),
            tenantId,
            planId,
            expectedVersion
        );
        if (updated == 0) {
            return 0;
        }
        jdbcTemplate.update(
            "delete from modeling_warehouse_plan_data_mart where tenant_id = ? and plan_id = ?",
            tenantId,
            planId
        );
        for (UUID dataMartId : dataMartIds) {
            jdbcTemplate.update(
                """
                insert into modeling_warehouse_plan_data_mart (
                    id, tenant_id, plan_id, data_mart_id, created_by, created_date
                ) values (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                planId,
                dataMartId,
                actorId,
                Timestamp.from(now)
            );
        }
        return 1;
    }

    private void replaceDomains(String tenantId, String actorId, UUID dataMartId, List<UUID> domainIds, Instant now) {
        jdbcTemplate.update("delete from modeling_data_mart_domain where tenant_id = ? and data_mart_id = ?", tenantId, dataMartId);
        for (UUID domainId : domainIds) {
            jdbcTemplate.update(
                """
                insert into modeling_data_mart_domain (
                    id, tenant_id, data_mart_id, domain_id, created_by, created_date
                ) values (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                dataMartId,
                domainId,
                actorId,
                Timestamp.from(now)
            );
        }
    }

    private void insertRevision(String tenantId, String actorId, View view, String snapshot) {
        jdbcTemplate.update(
            """
            insert into modeling_data_mart_revision (
                id, tenant_id, data_mart_id, revision, status, content_checksum,
                snapshot_json, created_by, created_date
            ) values (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            """,
            UUID.randomUUID(),
            tenantId,
            view.id(),
            view.revision(),
            view.status().name(),
            view.checksum(),
            snapshot,
            actorId,
            Timestamp.from(view.updatedAt())
        );
    }

    private StoredDataMart map(ResultSet row, int rowNumber) throws SQLException {
        return new StoredDataMart(
            row.getObject("id", UUID.class),
            row.getString("code"),
            row.getString("name"),
            row.getString("purpose"),
            row.getString("owner_id"),
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

    public record StoredDataMart(
        UUID id,
        String code,
        String name,
        String purpose,
        String ownerId,
        Status status,
        int revision,
        String checksum,
        String idempotencyKey,
        String idempotencyRequestHash,
        Instant createdAt,
        Instant updatedAt
    ) {}

    public record PlanBinding(UUID planId, List<UUID> dataMartIds, int version) {}
}
