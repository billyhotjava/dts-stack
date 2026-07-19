package com.yuzhi.dts.platform.repository.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitStatus;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitView;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL persistence adapter for the measurement-unit current head and revision ledger. */
@Repository
public class MeasurementUnitRepository {

    private static final String CURRENT_COLUMNS = """
        select id, code, name, symbol, quantity_kind, conversion_factor, base_unit_ref,
               precision, status, version, checksum, created_date, last_modified_date
          from governance_measurement_unit
        """;

    private final JdbcTemplate jdbcTemplate;

    public MeasurementUnitRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public Optional<StoredUnit> findCurrent(UUID unitId) {
        return jdbcTemplate
            .query(CURRENT_COLUMNS + " where id = ?", MeasurementUnitRepository::mapStoredUnit, unitId)
            .stream()
            .findFirst();
    }

    public Optional<StoredUnit> findByCode(String code) {
        return jdbcTemplate
            .query(
                CURRENT_COLUMNS + " where lower(code) = lower(?)",
                MeasurementUnitRepository::mapStoredUnit,
                code
            )
            .stream()
            .findFirst();
    }

    public List<StoredUnit> listCurrent() {
        return jdbcTemplate.query(CURRENT_COLUMNS + " order by code, id", MeasurementUnitRepository::mapStoredUnit);
    }

    /** Serialize the small owner graph so concurrent base-unit writes cannot create cross-row cycles. */
    public void lockMutationGraph() {
        jdbcTemplate.execute("select pg_advisory_xact_lock(6706001)");
    }

    public int insertCurrent(MeasurementUnitView view, String actorId) {
        return jdbcTemplate.update(
            """
            insert into governance_measurement_unit (
                id, code, name, symbol, quantity_kind, conversion_factor, base_unit_ref,
                precision, status, version, checksum, created_by, last_modified_by,
                created_date, last_modified_date
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            view.id(),
            view.code(),
            view.name(),
            view.symbol(),
            view.quantityKind(),
            view.conversionFactor(),
            view.baseUnitRef(),
            view.precision(),
            view.status().name(),
            view.version(),
            view.checksum(),
            actorId,
            actorId,
            Timestamp.from(view.createdAt()),
            Timestamp.from(view.updatedAt())
        );
    }

    public int compareAndSet(StoredUnit expected, MeasurementUnitView replacement, String actorId) {
        return jdbcTemplate.update(
            """
            update governance_measurement_unit
               set code = ?, name = ?, symbol = ?, quantity_kind = ?, conversion_factor = ?,
                   base_unit_ref = ?, precision = ?, status = ?, version = ?, checksum = ?,
                   last_modified_by = ?, last_modified_date = ?
             where id = ? and version = ? and checksum = ?
            """,
            replacement.code(),
            replacement.name(),
            replacement.symbol(),
            replacement.quantityKind(),
            replacement.conversionFactor(),
            replacement.baseUnitRef(),
            replacement.precision(),
            replacement.status().name(),
            replacement.version(),
            replacement.checksum(),
            actorId,
            Timestamp.from(replacement.updatedAt()),
            expected.id(),
            expected.version(),
            expected.checksum()
        );
    }

    public int insertRevision(MeasurementUnitView view, String snapshot, String actorId) {
        return jdbcTemplate.update(
            """
            insert into governance_measurement_unit_revision (
                measurement_unit_id, version, checksum, snapshot_json, created_by, created_date
            ) values (?, ?, ?, cast(? as jsonb), ?, ?)
            """,
            view.id(),
            view.version(),
            view.checksum(),
            snapshot,
            actorId,
            Timestamp.from(view.updatedAt())
        );
    }

    public List<StoredRevision> listRevisions(UUID unitId) {
        return jdbcTemplate.query(
            """
            select measurement_unit_id, version, checksum, snapshot_json::text as snapshot_json, created_date
              from governance_measurement_unit_revision
             where measurement_unit_id = ?
             order by version
            """,
            (row, rowNumber) ->
                new StoredRevision(
                    row.getObject("measurement_unit_id", UUID.class),
                    row.getInt("version"),
                    row.getString("checksum"),
                    row.getString("snapshot_json"),
                    instant(row, "created_date")
                ),
            unitId
        );
    }

    public List<UnitDependent> listUnitDependents(UUID unitId) {
        return jdbcTemplate.query(
            """
            select id, code, name, version, status
              from governance_measurement_unit
             where base_unit_ref = ?
             order by code, id
            """,
            (row, rowNumber) ->
                new UnitDependent(
                    row.getObject("id", UUID.class),
                    row.getString("code"),
                    row.getString("name"),
                    row.getInt("version"),
                    MeasurementUnitStatus.valueOf(row.getString("status"))
                ),
            unitId
        );
    }

    public List<ModelSpecReference> listModelSpecReferences(String tenantId, UUID unitId) {
        return jdbcTemplate.query(
            """
            select s.id as model_spec_id, s.plan_id, s.domain_id, s.name, s.status, s.revision,
                   (binding ->> 'measurementUnitVersion')::int as referenced_version
              from modeling_model_spec s
              cross join lateral jsonb_array_elements(coalesce(s.standard_bindings, '[]'::jsonb)) binding
             where s.contract_version = 2
               and s.tenant_id = ?
               and binding ->> 'measurementUnitId' = ?
             order by s.name, s.id
            """,
            (row, rowNumber) ->
                new ModelSpecReference(
                    row.getObject("model_spec_id", UUID.class),
                    row.getObject("plan_id", UUID.class),
                    row.getObject("domain_id", UUID.class),
                    row.getString("name"),
                    row.getString("status"),
                    row.getInt("revision"),
                    row.getInt("referenced_version")
                ),
            tenantId,
            unitId.toString()
        );
    }

    private static StoredUnit mapStoredUnit(ResultSet row, int rowNumber) throws SQLException {
        return new StoredUnit(
            row.getObject("id", UUID.class),
            row.getString("code"),
            row.getString("name"),
            row.getString("symbol"),
            row.getString("quantity_kind"),
            row.getBigDecimal("conversion_factor"),
            row.getObject("base_unit_ref", UUID.class),
            row.getInt("precision"),
            MeasurementUnitStatus.valueOf(row.getString("status")),
            row.getInt("version"),
            row.getString("checksum"),
            instant(row, "created_date"),
            instant(row, "last_modified_date")
        );
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp timestamp = row.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record StoredUnit(
        UUID id,
        String code,
        String name,
        String symbol,
        String quantityKind,
        BigDecimal conversionFactor,
        UUID baseUnitRef,
        int precision,
        MeasurementUnitStatus status,
        int version,
        String checksum,
        Instant createdAt,
        Instant updatedAt
    ) {}

    public record StoredRevision(UUID unitId, int version, String checksum, String snapshotJson, Instant createdAt) {}

    public record UnitDependent(
        UUID unitId,
        String code,
        String name,
        int version,
        MeasurementUnitStatus status
    ) {}

    public record ModelSpecReference(
        UUID modelSpecId,
        UUID planId,
        UUID domainId,
        String displayName,
        String status,
        int currentVersion,
        int referencedVersion
    ) {}
}
