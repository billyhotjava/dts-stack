package com.yuzhi.dts.platform.service.sprint64;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class Sprint64GovernanceService {

    private static final String PROCESS_ID_PATTERN = "[a-z0-9][a-z0-9_-]{1,63}";
    private static final String DIMENSION_ID_PATTERN = "[a-z0-9][a-z0-9_-]{1,127}";

    private final NamedParameterJdbcTemplate jdbc;

    public Sprint64GovernanceService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<BusinessProcessDto> listProcesses(UUID domainId) {
        return jdbc.query(
            """
            select id, domain_id, version, process_id, name, description,
                   source_type, source_id, source_version, confirmed,
                   created_date, last_modified_date
              from sprint64_business_process
             where domain_id = :domainId
             order by name asc, process_id asc
            """,
            params().addValue("domainId", domainId),
            (rs, rowNum) -> new BusinessProcessDto(
                rs.getObject("id", UUID.class),
                rs.getInt("version"),
                rs.getString("process_id"),
                rs.getObject("domain_id", UUID.class),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("source_type"),
                rs.getString("source_id"),
                rs.getString("source_version"),
                rs.getBoolean("confirmed"),
                instant(rs.getTimestamp("created_date")),
                instant(rs.getTimestamp("last_modified_date"))
            )
        );
    }

    public BusinessProcessDto createProcess(UUID domainId, BusinessProcessRequest request) {
        String processId = requiredProcessId(request == null ? null : request.processId());
        String name = required(request == null ? null : request.name(), "name");
        java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
        jdbc.update(
            """
            insert into sprint64_business_process
                (id, domain_id, version, process_id, name, description,
                 source_type, source_id, source_version, confirmed, created_date, last_modified_date)
            values (:id, :domainId, :version, :processId, :name, :description,
                    'MANUAL', null, null, true, :now, :now)
            """,
            params()
                .addValue("id", UUID.randomUUID())
                .addValue("domainId", domainId)
                .addValue("version", Sprint64GovernanceContract.VERSION)
                .addValue("processId", processId)
                .addValue("name", name)
                .addValue("description", trimToNull(request == null ? null : request.description()))
                .addValue("now", now)
        );
        return listProcesses(domainId).stream().filter(item -> item.processId().equals(processId)).findFirst().orElseThrow();
    }

    public void deleteProcess(UUID domainId, String processId) {
        jdbc.update(
            "delete from sprint64_business_process where domain_id = :domainId and process_id = :processId",
            params().addValue("domainId", domainId).addValue("processId", requiredProcessId(processId))
        );
        jdbc.update(
            "delete from sprint64_bus_matrix where domain_id = :domainId and process_id = :processId",
            params().addValue("domainId", domainId).addValue("processId", requiredProcessId(processId))
        );
    }

    @Transactional(readOnly = true)
    public List<Sprint64GovernanceContract.WarehouseLayerDto> listWarehouseLayers() {
        return Sprint64GovernanceContract.warehouseLayers();
    }

    @Transactional(readOnly = true)
    public List<Sprint64GovernanceContract.ConformedDimensionDto> listConformedDimensions(UUID domainId) {
        return jdbc.query(
            """
            select dimension_id, name, source_model, source_type, source_id, source_version, confirmed
              from sprint64_conformed_dimension
             where domain_id = :domainId
             order by name asc, dimension_id asc
            """,
            params().addValue("domainId", domainId),
            (rs, rowNum) ->
                new Sprint64GovernanceContract.ConformedDimensionDto(
                    rs.getString("dimension_id"),
                    rs.getString("name"),
                    rs.getString("source_model"),
                    List.of(domainId.toString()),
                    rs.getString("source_type"),
                    rs.getString("source_id"),
                    rs.getString("source_version"),
                    rs.getBoolean("confirmed")
                )
        );
    }

    public Sprint64GovernanceContract.ConformedDimensionDto createConformedDimension(
        UUID domainId,
        ConformedDimensionRequest request
    ) {
        String dimensionId = requiredDimensionId(request == null ? null : request.dimensionId());
        String name = required(request == null ? null : request.name(), "name");
        if (dimensionCount(domainId, dimensionId) > 0) {
            throw new IllegalArgumentException("一致性维度编码已存在: " + dimensionId);
        }
        java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
        jdbc.update(
            """
            insert into sprint64_conformed_dimension
                (domain_id, dimension_id, name, source_model,
                 source_type, source_id, source_version, confirmed, created_date, last_modified_date)
            values
                (:domainId, :dimensionId, :name, :sourceModel,
                 'MANUAL', null, null, true, :now, :now)
            """,
            params()
                .addValue("domainId", domainId)
                .addValue("dimensionId", dimensionId)
                .addValue("name", name)
                .addValue("sourceModel", trimToNull(request == null ? null : request.sourceModel()))
                .addValue("now", now)
        );
        return listConformedDimensions(domainId)
            .stream()
            .filter(item -> item.dimensionId().equals(dimensionId))
            .findFirst()
            .orElseThrow();
    }

    public void deleteConformedDimension(UUID domainId, String dimensionId) {
        String normalizedId = requiredDimensionId(dimensionId);
        Integer referenceCount = jdbc.queryForObject(
            """
            select count(*)
              from sprint64_bus_matrix
             where domain_id = :domainId
               and dimension_id = :dimensionId
               and enabled = true
            """,
            params().addValue("domainId", domainId).addValue("dimensionId", normalizedId),
            Integer.class
        );
        if (referenceCount != null && referenceCount > 0) {
            throw new DimensionInUseException(normalizedId);
        }
        jdbc.update(
            "delete from sprint64_bus_matrix where domain_id = :domainId and dimension_id = :dimensionId",
            params().addValue("domainId", domainId).addValue("dimensionId", normalizedId)
        );
        int deleted = jdbc.update(
            "delete from sprint64_conformed_dimension where domain_id = :domainId and dimension_id = :dimensionId",
            params().addValue("domainId", domainId).addValue("dimensionId", normalizedId)
        );
        if (deleted == 0) throw new IllegalArgumentException("一致性维度不存在: " + normalizedId);
    }

    public ModelingCandidateConfirmationResult confirmModelingCandidates(
        UUID domainId,
        ModelingCandidateConfirmationRequest request
    ) {
        if (request == null) throw new IllegalArgumentException("确认请求不能为空");
        List<String> processIds = normalizedIds(request.processIds(), Sprint64GovernanceService::requiredProcessId);
        List<String> dimensionIds = normalizedIds(request.dimensionIds(), Sprint64GovernanceService::requiredDimensionId);
        validateDomainIds(domainId, "sprint64_business_process", "process_id", processIds, "业务过程");
        validateDomainIds(domainId, "sprint64_conformed_dimension", "dimension_id", dimensionIds, "一致性维度");

        java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
        int confirmedProcesses = updateConfirmed(
            domainId,
            "sprint64_business_process",
            "process_id",
            processIds,
            now
        );
        int confirmedDimensions = updateConfirmed(
            domainId,
            "sprint64_conformed_dimension",
            "dimension_id",
            dimensionIds,
            now
        );
        return new ModelingCandidateConfirmationResult(confirmedProcesses, confirmedDimensions);
    }

    @Transactional(readOnly = true)
    public List<BusMatrixLinkDto> listBusMatrix(UUID domainId) {
        return jdbc.query(
            """
            select process_id, dimension_id, enabled
              from sprint64_bus_matrix
             where domain_id = :domainId
             order by process_id asc, dimension_id asc
            """,
            params().addValue("domainId", domainId),
            (rs, rowNum) -> new BusMatrixLinkDto(rs.getString("process_id"), rs.getString("dimension_id"), rs.getBoolean("enabled"))
        );
    }

    public BusMatrixLinkDto saveBusMatrix(UUID domainId, BusMatrixRequest request) {
        String processId = requiredProcessId(request == null ? null : request.processId());
        String dimensionId = requiredDimensionId(request == null ? null : request.dimensionId());
        BusinessProcessDto process = listProcesses(domainId)
            .stream()
            .filter(item -> item.processId().equals(processId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("业务过程不存在: " + processId));
        Sprint64GovernanceContract.ConformedDimensionDto dimension = listConformedDimensions(domainId)
            .stream()
            .filter(item -> item.dimensionId().equals(dimensionId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("一致性维度不存在: " + dimensionId));
        if (!process.confirmed()) throw new IllegalArgumentException("业务过程尚未确认: " + processId);
        if (!dimension.confirmed()) throw new IllegalArgumentException("一致性维度尚未确认: " + dimensionId);
        java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
        jdbc.update(
            """
            insert into sprint64_bus_matrix (domain_id, process_id, dimension_id, enabled, created_date, last_modified_date)
            values (:domainId, :processId, :dimensionId, :enabled, :now, :now)
            on conflict (domain_id, process_id, dimension_id)
            do update set enabled = excluded.enabled, last_modified_date = excluded.last_modified_date
            """,
            params()
                .addValue("domainId", domainId)
                .addValue("processId", processId)
                .addValue("dimensionId", dimensionId)
                .addValue("enabled", request.enabled())
                .addValue("now", now)
        );
        return new BusMatrixLinkDto(processId, dimensionId, request.enabled());
    }

    @Transactional(readOnly = true)
    public Sprint64GovernanceContract.GrainValidation validateGrain(GrainValidationRequest request) {
        return Sprint64GovernanceContract.validateGrain(
            request == null ? null : request.warehouseLayer(),
            request == null ? null : request.statement(),
            request == null ? List.of() : request.grainKeys()
        );
    }

    public static String requiredProcessId(String processId) {
        String value = required(processId, "processId").toLowerCase(java.util.Locale.ROOT);
        if (!value.matches(PROCESS_ID_PATTERN)) throw new IllegalArgumentException("processId 必须是 2-64 位小写字母、数字、下划线或连字符");
        return value;
    }

    public static String requiredDimensionId(String dimensionId) {
        String value = required(dimensionId, "dimensionId").toLowerCase(java.util.Locale.ROOT);
        if (!value.matches(DIMENSION_ID_PATTERN)) {
            throw new IllegalArgumentException("dimensionId 必须是 2-128 位小写字母、数字、下划线或连字符");
        }
        return value;
    }

    private int dimensionCount(UUID domainId, String dimensionId) {
        Integer count = jdbc.queryForObject(
            "select count(*) from sprint64_conformed_dimension where domain_id = :domainId and dimension_id = :dimensionId",
            params().addValue("domainId", domainId).addValue("dimensionId", dimensionId),
            Integer.class
        );
        return count == null ? 0 : count;
    }

    private void validateDomainIds(UUID domainId, String table, String idColumn, List<String> ids, String label) {
        if (ids.isEmpty()) return;
        Integer count = jdbc.queryForObject(
            "select count(*) from " + table + " where domain_id = :domainId and " + idColumn + " in (:ids)",
            params().addValue("domainId", domainId).addValue("ids", ids),
            Integer.class
        );
        if (count == null || count != ids.size()) throw new IllegalArgumentException(label + "不属于当前主题域");
    }

    private int updateConfirmed(
        UUID domainId,
        String table,
        String idColumn,
        List<String> ids,
        java.sql.Timestamp now
    ) {
        if (ids.isEmpty()) return 0;
        return jdbc.update(
            "update " + table + " set confirmed = true, last_modified_date = :now where domain_id = :domainId and " + idColumn + " in (:ids) and confirmed = false",
            params().addValue("domainId", domainId).addValue("ids", ids).addValue("now", now)
        );
    }

    private static List<String> normalizedIds(List<String> values, java.util.function.Function<String, String> normalizer) {
        if (values == null || values.isEmpty()) return List.of();
        Set<String> normalized = new LinkedHashSet<>();
        values.forEach(value -> normalized.add(normalizer.apply(value)));
        return List.copyOf(normalized);
    }

    private static String required(String value, String field) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(field + " 不能为空");
        return value.trim();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static Instant instant(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static MapSqlParameterSource params() {
        return new MapSqlParameterSource();
    }

    public record BusinessProcessDto(
        UUID id,
        int version,
        String processId,
        UUID domainId,
        String name,
        String description,
        String sourceType,
        String sourceId,
        String sourceVersion,
        boolean confirmed,
        Instant createdAt,
        Instant updatedAt
    ) {}

    public record BusinessProcessRequest(String processId, String name, String description) {}

    public record ConformedDimensionRequest(String dimensionId, String name, String sourceModel) {}

    public record ModelingCandidateConfirmationRequest(List<String> processIds, List<String> dimensionIds) {}

    public record ModelingCandidateConfirmationResult(int confirmedProcesses, int confirmedDimensions) {}

    public static final class DimensionInUseException extends RuntimeException {

        public DimensionInUseException(String dimensionId) {
            super("一致性维度已被总线矩阵引用: " + dimensionId);
        }
    }

    public record BusMatrixLinkDto(String processId, String dimensionId, boolean enabled) {}

    public record BusMatrixRequest(String processId, String dimensionId, boolean enabled) {}

    public record GrainValidationRequest(String warehouseLayer, String statement, List<String> grainKeys) {}
}
