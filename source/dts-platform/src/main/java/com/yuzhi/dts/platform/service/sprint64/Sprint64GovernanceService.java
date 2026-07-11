package com.yuzhi.dts.platform.service.sprint64;

import java.time.Instant;
import java.util.List;
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

    private final NamedParameterJdbcTemplate jdbc;

    public Sprint64GovernanceService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<BusinessProcessDto> listProcesses(UUID domainId) {
        return jdbc.query(
            """
            select id, domain_id, version, process_id, name, description, created_date, last_modified_date
              from sprint64_business_process
             where domain_id = :domainId
             order by name asc, process_id asc
            """,
            params().addValue("domainId", domainId),
            (rs, rowNum) -> new BusinessProcessDto(
                rs.getInt("version"),
                rs.getString("process_id"),
                rs.getObject("domain_id", UUID.class),
                rs.getString("name"),
                rs.getString("description"),
                instant(rs.getTimestamp("created_date")),
                instant(rs.getTimestamp("last_modified_date"))
            )
        );
    }

    public BusinessProcessDto createProcess(UUID domainId, BusinessProcessRequest request) {
        String processId = requiredProcessId(request == null ? null : request.processId());
        String name = required(request == null ? null : request.name(), "name");
        Instant now = Instant.now();
        jdbc.update(
            """
            insert into sprint64_business_process
                (id, domain_id, version, process_id, name, description, created_date, last_modified_date)
            values (:id, :domainId, :version, :processId, :name, :description, :now, :now)
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
        return Sprint64GovernanceContract.conformedDimensions();
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
        String dimensionId = required(request == null ? null : request.dimensionId(), "dimensionId");
        if (listProcesses(domainId).stream().noneMatch(item -> item.processId().equals(processId))) {
            throw new IllegalArgumentException("业务过程不存在: " + processId);
        }
        if (Sprint64GovernanceContract.conformedDimensions().stream().noneMatch(item -> item.dimensionId().equals(dimensionId))) {
            throw new IllegalArgumentException("一致性维度不存在: " + dimensionId);
        }
        Instant now = Instant.now();
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
        int version,
        String processId,
        UUID domainId,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt
    ) {}

    public record BusinessProcessRequest(String processId, String name, String description) {}

    public record BusMatrixLinkDto(String processId, String dimensionId, boolean enabled) {}

    public record BusMatrixRequest(String processId, String dimensionId, boolean enabled) {}

    public record GrainValidationRequest(String warehouseLayer, String statement, List<String> grainKeys) {}
}
