package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerContract.StoredWarehouseLayer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Global custom-only warehouse layer persistence. The registry intentionally has no tenant column;
 * built-in layers remain code-owned by {@code Sprint64GovernanceContract}.
 */
@Repository
public class WarehouseLayerRepository {

    private static final String COLUMNS =
        "id, code, name, system_layer_code, description, naming_prefix, status, version, " +
        "created_by, created_date, last_modified_by, last_modified_date";

    private final JdbcTemplate jdbcTemplate;

    public WarehouseLayerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<StoredWarehouseLayer> findAllActive() {
        return jdbcTemplate.query(
            "select " + COLUMNS + " from modeling_warehouse_layer where status = 'ACTIVE' " +
                "order by system_layer_code, name, code",
            (rs, rowNum) -> map(rs)
        );
    }

    public Optional<StoredWarehouseLayer> findByCode(String code) {
        return jdbcTemplate
            .query("select " + COLUMNS + " from modeling_warehouse_layer where code = ?", (rs, rowNum) -> map(rs), code)
            .stream()
            .findFirst();
    }

    public boolean codeExists(String code) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from modeling_warehouse_layer where code = ?",
            Integer.class,
            code
        );
        return count != null && count > 0;
    }

    public int insert(StoredWarehouseLayer row) {
        return jdbcTemplate.update(
            "insert into modeling_warehouse_layer (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            row.id(),
            row.code(),
            row.name(),
            row.systemLayerCode(),
            row.description(),
            row.namingPrefix(),
            row.status(),
            row.version(),
            row.createdBy(),
            Timestamp.from(row.createdDate()),
            row.lastModifiedBy(),
            Timestamp.from(row.lastModifiedDate())
        );
    }

    public long countActiveModelReferences(String code) {
        Long count = jdbcTemplate.queryForObject(
            "select count(*) from modeling_model_spec where warehouse_layer_code = ? and status <> 'ARCHIVED'",
            Long.class,
            code
        );
        return count == null ? 0L : count;
    }

    public int softDelete(String code, int expectedVersion, String actorId, Instant modifiedAt) {
        return jdbcTemplate.update(
            "update modeling_warehouse_layer " +
                "set status = 'DELETED', version = version + 1, last_modified_by = ?, last_modified_date = ? " +
                "where code = ? and version = ? and status = 'ACTIVE'",
            actorId,
            Timestamp.from(modifiedAt),
            code,
            expectedVersion
        );
    }

    private static StoredWarehouseLayer map(ResultSet rs) throws SQLException {
        return new StoredWarehouseLayer(
            rs.getObject("id", UUID.class),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("system_layer_code"),
            rs.getString("description"),
            rs.getString("naming_prefix"),
            rs.getString("status"),
            rs.getInt("version"),
            rs.getString("created_by"),
            toInstant(rs.getTimestamp("created_date")),
            rs.getString("last_modified_by"),
            toInstant(rs.getTimestamp("last_modified_date"))
        );
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
