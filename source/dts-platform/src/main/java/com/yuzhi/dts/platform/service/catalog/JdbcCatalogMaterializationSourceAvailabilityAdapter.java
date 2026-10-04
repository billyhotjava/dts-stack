package com.yuzhi.dts.platform.service.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.catalog.CatalogMaterializationSourceAvailabilityPort.CurrentSource;
import com.yuzhi.dts.platform.service.catalog.CatalogMaterializationSourceAvailabilityPort.ExpectedSource;
import com.yuzhi.dts.platform.service.catalog.CatalogMaterializationSourceAvailabilityPort.GenerationDrift;
import com.yuzhi.dts.platform.service.catalog.CatalogMaterializationSourceAvailabilityPort.SourceDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/** PostgreSQL adapter that linearizes materialization success against rollback availability writes. */
@Component
public class JdbcCatalogMaterializationSourceAvailabilityAdapter
    implements CatalogMaterializationSourceAvailabilityPort {

    private static final String LEGACY_EVENT_ID = "legacy-default";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcCatalogMaterializationSourceAvailabilityAdapter(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
    }

    @Override
    public List<CurrentSource> lockAndRead(List<SourceDescriptor> sources) {
        requireTransaction();
        List<SourceDescriptor> ordered = orderedDescriptors(sources);
        lockAvailabilityWrites();
        List<CurrentSource> result = new ArrayList<>(ordered.size());
        for (SourceDescriptor source : ordered) {
            AssetIdentity identity = resolveIdentity(source);
            Availability current = readCurrent(identity.assetType(), identity.assetKey());
            result.add(
                new CurrentSource(
                    source.sourceBindingId(),
                    identity.assetType(),
                    identity.assetKey(),
                    current.status(),
                    current.epoch(),
                    current.sourceSequence(),
                    current.eventId()
                )
            );
        }
        return List.copyOf(result);
    }

    @Override
    public List<GenerationDrift> lockAndCompare(List<ExpectedSource> expectedSources) {
        requireTransaction();
        List<ExpectedSource> ordered = orderedExpected(expectedSources);
        lockAvailabilityWrites();
        List<GenerationDrift> drift = new ArrayList<>();
        for (ExpectedSource expected : ordered) {
            Availability current = readCurrent(expected.assetType(), expected.assetKey());
            if (
                !Objects.equals(expected.expectedStatus(), current.status()) ||
                expected.expectedEpoch() != current.epoch() ||
                expected.expectedSourceSequence() != current.sourceSequence() ||
                !Objects.equals(expected.expectedEventId(), current.eventId())
            ) {
                drift.add(
                    new GenerationDrift(
                        expected.sourceBindingId(),
                        expected.assetType(),
                        expected.assetKey(),
                        expected.expectedStatus(),
                        expected.expectedEpoch(),
                        expected.expectedSourceSequence(),
                        expected.expectedEventId(),
                        current.status(),
                        current.epoch(),
                        current.sourceSequence(),
                        current.eventId()
                    )
                );
            }
        }
        return List.copyOf(drift);
    }

    private AssetIdentity resolveIdentity(SourceDescriptor source) {
        if (
            source == null ||
            source.sourceBindingId() == null ||
            !StringUtils.hasText(source.sourceType()) ||
            !StringUtils.hasText(source.sourceId())
        ) {
            throw new IllegalArgumentException("source descriptor is incomplete");
        }
        JsonNode locator = locator(source.locatorJson());
        return switch (source.sourceType()) {
            case "CATALOG_TABLE" -> resolveCatalogTable(source, locator);
            case "CONNECTION_TABLE" -> resolveConnectionTable(locator);
            case "DBT_NODE" -> resolveDbtNode(source, locator);
            default -> throw new IllegalArgumentException(
                "source type is not availability-managed: " + source.sourceType()
            );
        };
    }

    private AssetIdentity resolveCatalogTable(SourceDescriptor source, JsonNode locator) {
        UUID tableId = uuid(text(locator, "assetId"));
        if (tableId == null) {
            tableId = uuid(source.sourceId());
        }
        if (tableId == null) {
            throw new IllegalArgumentException("catalog table identity is invalid");
        }
        List<DatasetIdentity> rows = jdbcTemplate.query(
            """
            select d.source_id, d.hive_database, d.hive_table, d.name
              from catalog_table_schema t
              join catalog_dataset d on d.id = t.dataset_id
             where t.id = ?
            """,
            (row, rowNumber) ->
                new DatasetIdentity(
                    row.getObject("source_id", UUID.class),
                    row.getString("hive_database"),
                    row.getString("hive_table"),
                    row.getString("name")
                ),
            tableId
        );
        return datasetIdentity(rows, "catalog table");
    }

    private AssetIdentity resolveConnectionTable(JsonNode locator) {
        UUID connectionId = uuid(text(locator, "connectionId"));
        String namespace = text(locator, "namespace");
        String objectName = text(locator, "objectName");
        if (connectionId == null || !StringUtils.hasText(namespace) || !StringUtils.hasText(objectName)) {
            throw new IllegalArgumentException("connection table identity is invalid");
        }
        List<DatasetIdentity> rows = jdbcTemplate.query(
            """
            select source_id, hive_database, hive_table, name
              from catalog_dataset
             where source_id = ?
               and lower(hive_database) = lower(?)
               and lower(hive_table) = lower(?)
            """,
            (row, rowNumber) ->
                new DatasetIdentity(
                    row.getObject("source_id", UUID.class),
                    row.getString("hive_database"),
                    row.getString("hive_table"),
                    row.getString("name")
                ),
            connectionId,
            namespace.trim(),
            objectName.trim()
        );
        return datasetIdentity(rows, "connection table");
    }

    private AssetIdentity resolveDbtNode(SourceDescriptor source, JsonNode locator) {
        String uniqueId = text(locator, "uniqueId");
        if (!StringUtils.hasText(uniqueId)) {
            String sourceId = source.sourceId().trim();
            int separator = sourceId.indexOf(':');
            uniqueId = separator >= 0 && separator < sourceId.length() - 1
                ? sourceId.substring(separator + 1)
                : sourceId;
        }
        if (!StringUtils.hasText(uniqueId)) {
            throw new IllegalArgumentException("dbt source identity is invalid");
        }
        return new AssetIdentity(
            CatalogAssetType.DBT_MODEL,
            CatalogAssetKey.dbtModel(uniqueId, uniqueId)
        );
    }

    private AssetIdentity datasetIdentity(List<DatasetIdentity> rows, String label) {
        if (rows == null || rows.size() != 1) {
            throw new IllegalArgumentException(label + " does not resolve to one catalog dataset");
        }
        DatasetIdentity dataset = rows.getFirst();
        return new AssetIdentity(
            CatalogAssetType.DATASET,
            CatalogAssetKey.dataset(
                dataset.sourceId(),
                dataset.database(),
                dataset.database(),
                dataset.table(),
                dataset.name()
            )
        );
    }

    private Availability readCurrent(CatalogAssetType assetType, String assetKey) {
        List<Availability> rows = jdbcTemplate.query(
            """
            select status, availability_epoch, source_sequence, event_id
              from catalog_asset_availability
             where asset_type = ? and asset_key = ?
            """,
            (row, rowNumber) ->
                new Availability(
                    row.getString("status"),
                    row.getLong("availability_epoch"),
                    row.getLong("source_sequence"),
                    row.getString("event_id")
                ),
            assetType.name(),
            assetKey
        );
        if (rows.size() > 1) {
            throw new IllegalStateException("catalog availability identity is not unique");
        }
        return rows.isEmpty()
            ? new Availability("AVAILABLE", 0L, 0L, LEGACY_EVENT_ID)
            : rows.getFirst();
    }

    private void lockAvailabilityWrites() {
        jdbcTemplate.execute("lock table catalog_asset_availability in share mode");
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("catalog availability guard requires an active transaction");
        }
    }

    private JsonNode locator(String json) {
        if (!StringUtils.hasText(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode value = objectMapper.readTree(json);
            if (value == null || !value.isObject()) {
                throw new IllegalArgumentException("source locator is invalid");
            }
            return value;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("source locator is invalid", exception);
        }
    }

    private static List<SourceDescriptor> orderedDescriptors(List<SourceDescriptor> sources) {
        if (sources == null) {
            throw new IllegalArgumentException("sources are required");
        }
        return sources
            .stream()
            .sorted(Comparator.comparing(source -> source.sourceBindingId().toString()))
            .toList();
    }

    private static List<ExpectedSource> orderedExpected(List<ExpectedSource> sources) {
        if (sources == null) {
            throw new IllegalArgumentException("expected sources are required");
        }
        return sources
            .stream()
            .sorted(
                Comparator.comparing((ExpectedSource source) -> source.assetType().name())
                    .thenComparing(ExpectedSource::assetKey)
                    .thenComparing(source -> source.sourceBindingId().toString())
            )
            .toList();
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static UUID uuid(String value) {
        try {
            return StringUtils.hasText(value) ? UUID.fromString(value.trim()) : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private record AssetIdentity(CatalogAssetType assetType, String assetKey) {}

    private record DatasetIdentity(UUID sourceId, String database, String table, String name) {}

    private record Availability(String status, long epoch, long sourceSequence, String eventId) {}
}
