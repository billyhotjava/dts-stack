package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CatalogAssetTagPermissionReadAdapter {

    private static final Pattern LEGACY_DATASET_KEY = Pattern.compile(
        "^source:([^/]+)/schema:([^/]+)/table:([^/]+)$"
    );
    private static final int MAX_MATCHES_PER_KEY = 2;

    private final JdbcTemplate jdbcTemplate;

    public CatalogAssetTagPermissionReadAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SqlModelIdentity> findSqlModelsByLowerName(String naturalKey) {
        if (!StringUtils.hasText(naturalKey)) {
            return List.of();
        }
        return jdbcTemplate.query(
            """
            select id, name
              from modeling_sql_model
             where lower(name) = ?
             order by id
             fetch first 2 rows only
            """,
            (resultSet, rowNumber) ->
                new SqlModelIdentity(resultSet.getObject("id", UUID.class), resultSet.getString("name")),
            naturalKey.trim().toLowerCase(Locale.ROOT)
        );
    }

    public BatchLookup loadBatch(BatchLookupRequest request) {
        BatchLookupRequest safeRequest = request == null
            ? new BatchLookupRequest(Set.of(), Set.of(), Map.of())
            : request;
        Map<CatalogAssetType, List<PermissionIdentity>> identities = new EnumMap<>(
            CatalogAssetType.class
        );
        for (Map.Entry<CatalogAssetType, Set<String>> entry : safeRequest.naturalKeys().entrySet()) {
            List<PermissionIdentity> matches = findPermissionIdentities(
                entry.getKey(),
                entry.getValue()
            );
            if (!matches.isEmpty()) {
                identities.put(entry.getKey(), matches);
            }
        }
        return new BatchLookup(
            findLegacyDatasetCandidates(safeRequest.legacyDatasetKeys()),
            findOpenMetadataDatasetCandidates(safeRequest.openMetadataDatasetKeys()),
            identities
        );
    }

    private List<PermissionIdentity> findPermissionIdentities(
        CatalogAssetType type,
        Set<String> naturalKeys
    ) {
        if (naturalKeys == null || naturalKeys.isEmpty()) {
            return List.of();
        }
        return switch (type) {
            case CATALOG_DOMAIN ->
                findUuidCodeIdentities("catalog_domain", "code", naturalKeys);
            case DBT_MODEL ->
                findUuidCodeIdentities(
                    "modeling_model_implementation",
                    "dbt_unique_id",
                    naturalKeys
                );
            case BI_DATASET ->
                findUuidOnlyIdentities("query_dataset_asset", naturalKeys);
            case SCREEN -> findScreenIdentities(naturalKeys);
            case METRIC -> findMetricIdentities(naturalKeys);
            case METRIC_PACK -> findExternalIdentities(type, naturalKeys);
            case SEMANTIC_MODEL ->
                findUuidOnlyIdentities("semantic_model", naturalKeys);
            case DATA_PRODUCT ->
                findUuidCodeIdentities(
                    "catalog_data_product",
                    "code",
                    naturalKeys
                );
            case MODELING_SQL_MODEL ->
                findUuidCodeIdentities("modeling_sql_model", "name", naturalKeys);
            case MODELING_PLAN ->
                findUuidOnlyIdentities("modeling_plan", naturalKeys);
            case DATA_STANDARD ->
                findUuidCodeIdentities("data_standard", "code", naturalKeys);
            case METADATA_STANDARD ->
                findUuidOnlyIdentities("metadata_standard", naturalKeys);
            case GLOSSARY_TERM ->
                findUuidCodeIdentities("modeling_glossary_term", "code", naturalKeys);
            case GOV_INDICATOR ->
                findUuidCodeIdentities("gov_indicator_definition", "code", naturalKeys);
            case GOV_INDICATOR_TEMPLATE ->
                findUuidCodeIdentities(
                    "gov_indicator_template",
                    "code",
                    naturalKeys
                );
            case QUALITY_RULE ->
                findUuidCodeIdentities("gov_rule", "code", naturalKeys);
            case SECURITY_POLICY ->
                findSecurityPolicyIdentities(naturalKeys);
            case API_SERVICE -> findUuidCodeIdentities("svc_api", "code", naturalKeys);
            case BACKFILL_REQUEST ->
                findUuidOnlyIdentities("ops_backfill_request", naturalKeys);
            case DATASET -> List.of();
        };
    }

    private List<PermissionIdentity> findUuidCodeIdentities(
        String table,
        String naturalKeyColumn,
        Set<String> naturalKeys
    ) {
        List<String> keys = normalizedValues(naturalKeys);
        if (keys.isEmpty()) {
            return List.of();
        }
        List<Object> arguments = new ArrayList<>(keys.size() + 1);
        arguments.addAll(keys);
        arguments.add(boundedResultLimit(keys.size()));
        String sql =
            """
            with requested_natural_key(natural_key) as (
                values """ +
            String.join(
                ",",
                Collections.nCopies(keys.size(), "(cast(? as text))")
            ) +
            """
            )
            select id, natural_key
              from (
                    select candidate.id,
                           candidate.""" +
            naturalKeyColumn +
            " as natural_key,\n" +
            """
                           row_number() over (
                               partition by requested.natural_key
                               order by candidate.id
                           ) as match_rank
                      from requested_natural_key requested
                      join """ +
            " " +
            table +
            " candidate\n" +
            """
                        on lower(candidate.""" +
            naturalKeyColumn +
            ") = requested.natural_key\n" +
            """
                   ) matches
             where match_rank <= 2
             order by id
             fetch first ? rows only
            """;
        return jdbcTemplate.query(
            sql,
            (resultSet, rowNumber) ->
                new PermissionIdentity(
                    uuid(resultSet, "id").toString(),
                    resultSet.getString("natural_key")
                ),
            arguments.toArray()
        );
    }

    private List<PermissionIdentity> findScreenIdentities(Set<String> screenIds) {
        List<String> codes = normalizedValues(screenIds)
            .stream()
            .map(screenId -> "screen-" + screenId)
            .toList();
        if (codes.isEmpty()) {
            return List.of();
        }
        String sql =
            "select code from bi_report_link " +
            "where enabled = true and upper(report_type) = 'SCREEN' " +
            "and lower(code) in (" +
            placeholders(codes.size()) +
            ") order by id";
        return jdbcTemplate.query(
            sql,
            (resultSet, rowNumber) -> {
                String code = resultSet.getString("code");
                String screenId = code == null || code.length() <= "screen-".length()
                    ? null
                    : code.substring("screen-".length());
                return new PermissionIdentity(screenId, screenId);
            },
            codes.toArray()
        );
    }

    private List<PermissionIdentity> findUuidOnlyIdentities(
        String table,
        Set<String> naturalKeys
    ) {
        List<UUID> ids = uuidValues(naturalKeys);
        if (ids.isEmpty()) {
            return List.of();
        }
        String sql =
            "select id from " +
            table +
            " where id in (" +
            placeholders(ids.size()) +
            ") order by id";
        return jdbcTemplate.query(
            sql,
            (resultSet, rowNumber) -> {
                String id = uuid(resultSet, "id").toString();
                return new PermissionIdentity(id, id);
            },
            ids.toArray()
        );
    }

    private List<PermissionIdentity> findMetricIdentities(
        Set<String> canonicalKeys
    ) {
        List<PermissionIdentity> identities = new ArrayList<>(
            findExternalIdentities(CatalogAssetType.METRIC, canonicalKeys)
        );
        Map<String, String> localCodes = new java.util.LinkedHashMap<>();
        for (String key : canonicalKeys) {
            if (
                key != null &&
                key.startsWith("metric:local/") &&
                key.length() > "metric:local/".length()
            ) {
                localCodes.put(
                    key.substring("metric:local/".length()).toLowerCase(Locale.ROOT),
                    key
                );
            }
        }
        if (localCodes.isEmpty()) {
            return List.copyOf(identities);
        }
        String sql =
            "select id, code from semantic_metric where lower(code) in (" +
            placeholders(localCodes.size()) +
            ") order by id";
        identities.addAll(
            jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> {
                    String code = resultSet.getString("code");
                    return new PermissionIdentity(
                        uuid(resultSet, "id").toString(),
                        code,
                        CatalogAssetKey.metric("local", code),
                        null,
                        null
                    );
                },
                localCodes.keySet().toArray()
            )
        );
        return List.copyOf(identities);
    }

    private List<PermissionIdentity> findExternalIdentities(
        CatalogAssetType type,
        Set<String> canonicalKeys
    ) {
        List<String> keys = normalizedValues(canonicalKeys);
        if (keys.isEmpty()) {
            return List.of();
        }
        String sql =
            """
            select remote_asset_id, canonical_asset_key
              from catalog_external_asset_identity
             where asset_type = ?
               and active = true
               and lower(canonical_asset_key) in (""" +
            placeholders(keys.size()) +
            ") order by id";
        List<Object> arguments = new ArrayList<>(keys.size() + 1);
        arguments.add(type.name());
        arguments.addAll(keys);
        return jdbcTemplate.query(
            sql,
            (resultSet, rowNumber) ->
                new PermissionIdentity(
                    resultSet.getString("remote_asset_id"),
                    resultSet.getString("canonical_asset_key"),
                    resultSet.getString("canonical_asset_key"),
                    null,
                    null
                ),
            arguments.toArray()
        );
    }

    private List<PermissionIdentity> findSecurityPolicyIdentities(
        Set<String> datasetIds
    ) {
        List<UUID> ids = uuidValues(datasetIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        String sql =
            "select id, source_id, hive_database, hive_table, name, owner_dept " +
            "from catalog_dataset where id in (" +
            placeholders(ids.size()) +
            ") and row_security_policy is not null " +
            "and btrim(row_security_policy) <> '' order by id";
        return jdbcTemplate.query(
            sql,
            (resultSet, rowNumber) -> {
                CatalogDataset dataset = mapDataset(resultSet);
                String id = dataset.getId().toString();
                return new PermissionIdentity(
                    id,
                    id,
                    CatalogAssetKey.codeAsset(
                        CatalogAssetType.SECURITY_POLICY,
                        "default",
                        id
                    ),
                    CatalogAssetType.DATASET.name(),
                    dataset
                );
            },
            ids.toArray()
        );
    }

    private List<CatalogDataset> findLegacyDatasetCandidates(Set<String> canonicalKeys) {
        if (canonicalKeys == null || canonicalKeys.isEmpty()) {
            return List.of();
        }
        Set<LegacyDatasetLookupKey> lookupKeys = new LinkedHashSet<>();
        for (String key : canonicalKeys) {
            Matcher matcher = LEGACY_DATASET_KEY.matcher(key);
            if (!matcher.matches()) {
                continue;
            }
            String source = matcher.group(1);
            try {
                lookupKeys.add(
                    new LegacyDatasetLookupKey(
                        "unknown".equals(source)
                            ? null
                            : UUID.fromString(source),
                        matcher.group(2),
                        matcher.group(3)
                    )
                );
            } catch (IllegalArgumentException ignored) {
                // Invalid canonical keys remain unresolved and fail closed in the resolver.
            }
        }
        if (lookupKeys.isEmpty()) {
            return List.of();
        }
        List<Object> arguments = new ArrayList<>(lookupKeys.size() * 3 + 1);
        for (LegacyDatasetLookupKey lookupKey : lookupKeys) {
            arguments.add(lookupKey.sourceId());
            arguments.add(lookupKey.schemaName());
            arguments.add(lookupKey.tableName());
        }
        arguments.add(boundedResultLimit(lookupKeys.size()));
        String normalizedSchema = normalizedSegmentSql(
            "coalesce(nullif(btrim(d.hive_database), ''), 'default')"
        );
        String normalizedTable = normalizedSegmentSql(
            "coalesce(nullif(btrim(d.hive_table), ''), nullif(btrim(d.name), ''))"
        );
        String sql =
            """
            with requested_legacy_dataset(source_id, schema_name, table_name) as (
                values """ +
            String.join(
                ",",
                Collections.nCopies(
                    lookupKeys.size(),
                    "(cast(? as uuid), cast(? as text), cast(? as text))"
                )
            ) +
            """
            )
            select id, source_id, hive_database, hive_table, name, owner_dept
              from (
                    select d.id,
                           d.source_id,
                           d.hive_database,
                           d.hive_table,
                           d.name,
                           d.owner_dept,
                           row_number() over (
                               partition by r.source_id, r.schema_name, r.table_name
                               order by d.id
                           ) as match_rank
                      from requested_legacy_dataset r
                      join catalog_dataset d
                        on d.source_id is not distinct from r.source_id
                       and """ +
            " " +
            normalizedSchema +
            " = r.schema_name" +
            """

                       and """ +
            " " +
            normalizedTable +
            """
             = r.table_name
                   ) matches
             where match_rank <= 2
             order by id
             fetch first ? rows only
            """;
        return jdbcTemplate.query(
            sql,
            (resultSet, rowNumber) -> mapDataset(resultSet),
            arguments.toArray()
        );
    }

    private List<OpenMetadataDatasetIdentity> findOpenMetadataDatasetCandidates(
        Set<String> canonicalKeys
    ) {
        if (canonicalKeys == null || canonicalKeys.isEmpty()) {
            return List.of();
        }
        List<String> normalizedFqns = canonicalKeys
            .stream()
            .filter(key -> key != null && key.startsWith("om:") && key.length() > 3)
            .map(key ->
                key.substring("om:".length()).trim().toLowerCase(Locale.ROOT)
            )
            .distinct()
            .toList();
        if (normalizedFqns.isEmpty()) {
            return List.of();
        }
        List<Object> arguments = new ArrayList<>(normalizedFqns.size() + 1);
        arguments.addAll(normalizedFqns);
        arguments.add(boundedResultLimit(normalizedFqns.size()));
        String normalizedFqn = normalizedSegmentSql("o.fqn");
        String sql =
            """
            with requested_openmetadata_dataset(canonical_fqn) as (
                values """ +
            String.join(
                ",",
                Collections.nCopies(
                    normalizedFqns.size(),
                    "(cast(? as text))"
                )
            ) +
            """
            )
            select om_id,
                   om_fqn,
                   mapping_id,
                   legacy_dataset_id,
                   dataset_id,
                   source_id,
                   hive_database,
                   hive_table,
                   name,
                   owner_dept
              from (
                    select o.id as om_id,
                           o.fqn as om_fqn,
                           m.id as mapping_id,
                           m.legacy_dataset_id,
                           d.id as dataset_id,
                           d.source_id,
                           d.hive_database,
                           d.hive_table,
                           d.name,
                           d.owner_dept,
                           row_number() over (
                               partition by r.canonical_fqn
                               order by o.id, m.id
                           ) as match_rank
                      from requested_openmetadata_dataset r
                      join om_asset_cache o
                        on """ +
            " " +
            normalizedFqn +
            """
             = r.canonical_fqn
                      left join catalog_asset_mapping m
                        on lower(m.fqn) = lower(o.fqn)
                      left join catalog_dataset d
                        on d.id = m.legacy_dataset_id
                   ) matches
             where match_rank <= 2
             order by om_id, mapping_id
             fetch first ? rows only
            """;
        return jdbcTemplate.query(
            sql,
            (resultSet, rowNumber) -> {
                UUID datasetId = uuid(resultSet, "dataset_id");
                return new OpenMetadataDatasetIdentity(
                    uuid(resultSet, "om_id"),
                    resultSet.getString("om_fqn"),
                    uuid(resultSet, "mapping_id"),
                    uuid(resultSet, "legacy_dataset_id"),
                    datasetId == null ? null : mapDataset(resultSet)
                );
            },
            arguments.toArray()
        );
    }

    private String normalizedSegmentSql(String expression) {
        return (
            "regexp_replace(" +
            "regexp_replace(" +
            "regexp_replace(lower(btrim(" +
            expression +
            ")), '[^a-z0-9_.:-]+', '_', 'g'), " +
            "'_+', '_', 'g'), " +
            "'^_+|_+$', '', 'g')"
        );
    }

    private int boundedResultLimit(int keyCount) {
        return keyCount * MAX_MATCHES_PER_KEY + 1;
    }

    private CatalogDataset mapDataset(ResultSet resultSet, int rowNumber) throws SQLException {
        return mapDataset(resultSet);
    }

    private CatalogDataset mapDataset(ResultSet resultSet) throws SQLException {
        CatalogDataset dataset = new CatalogDataset();
        UUID id = uuid(resultSet, columnPresent(resultSet, "dataset_id") ? "dataset_id" : "id");
        dataset.setId(id);
        dataset.setSourceId(uuid(resultSet, "source_id"));
        dataset.setHiveDatabase(resultSet.getString("hive_database"));
        dataset.setHiveTable(resultSet.getString("hive_table"));
        dataset.setName(resultSet.getString("name"));
        dataset.setOwnerDept(resultSet.getString("owner_dept"));
        return dataset;
    }

    private boolean columnPresent(ResultSet resultSet, String column) throws SQLException {
        try {
            resultSet.findColumn(column);
            return true;
        } catch (SQLException ignored) {
            return false;
        }
    }

    private UUID uuid(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        if (value == null) {
            return null;
        }
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    private String placeholders(int size) {
        return String.join(",", Collections.nCopies(size, "?"));
    }

    private List<String> normalizedValues(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values
            .stream()
            .filter(StringUtils::hasText)
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .distinct()
            .toList();
    }

    private List<UUID> uuidValues(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            try {
                ids.add(UUID.fromString(value.trim()));
            } catch (IllegalArgumentException ignored) {
                // Invalid identities stay unresolved and fail closed in the resolver.
            }
        }
        return ids.stream().distinct().toList();
    }

    public record BatchLookupRequest(
        Set<String> legacyDatasetKeys,
        Set<String> openMetadataDatasetKeys,
        Map<CatalogAssetType, Set<String>> naturalKeys
    ) {
        public BatchLookupRequest {
            legacyDatasetKeys = immutableSet(legacyDatasetKeys);
            openMetadataDatasetKeys = immutableSet(openMetadataDatasetKeys);
            naturalKeys = immutableNaturalKeys(naturalKeys);
        }

        private static Set<String> immutableSet(Set<String> values) {
            return values == null ? Set.of() : Set.copyOf(values);
        }

        private static Map<CatalogAssetType, Set<String>> immutableNaturalKeys(
            Map<CatalogAssetType, Set<String>> values
        ) {
            if (values == null || values.isEmpty()) {
                return Map.of();
            }
            Map<CatalogAssetType, Set<String>> copy = new EnumMap<>(CatalogAssetType.class);
            values.forEach((type, keys) -> {
                if (type != null && keys != null && !keys.isEmpty()) {
                    copy.put(type, Set.copyOf(keys));
                }
            });
            return Collections.unmodifiableMap(copy);
        }
    }

    public record BatchLookup(
        List<CatalogDataset> legacyDatasets,
        List<OpenMetadataDatasetIdentity> openMetadataDatasets,
        Map<CatalogAssetType, List<PermissionIdentity>> identities
    ) {
        public BatchLookup {
            legacyDatasets = legacyDatasets == null ? List.of() : List.copyOf(legacyDatasets);
            openMetadataDatasets = openMetadataDatasets == null
                ? List.of()
                : List.copyOf(openMetadataDatasets);
            if (identities == null || identities.isEmpty()) {
                identities = Map.of();
            } else {
                Map<CatalogAssetType, List<PermissionIdentity>> copy = new EnumMap<>(
                    CatalogAssetType.class
                );
                identities.forEach((type, values) ->
                    copy.put(type, values == null ? List.of() : List.copyOf(values))
                );
                identities = Collections.unmodifiableMap(copy);
            }
        }
    }

    public record PermissionIdentity(
        String id,
        String naturalKey,
        String canonicalAssetKey,
        String grantAssetType,
        CatalogDataset dataset
    ) {
        public PermissionIdentity(String id, String naturalKey) {
            this(id, naturalKey, null, null, null);
        }
    }

    public record OpenMetadataDatasetIdentity(
        UUID openMetadataId,
        String fqn,
        UUID mappingId,
        UUID legacyDatasetId,
        CatalogDataset legacyDataset
    ) {}

    private record LegacyDatasetLookupKey(
        UUID sourceId,
        String schemaName,
        String tableName
    ) {}

    public record SqlModelIdentity(UUID id, String name) {}
}
