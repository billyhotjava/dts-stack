package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagPermissionReadAdapter.BatchLookupRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class CatalogAssetTagPermissionReadAdapterTest {

    private static final UUID SOURCE_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );

    @Test
    void fiveHundredKeysOfOneTypeExecuteOneBoundedQuery() {
        CountingJdbcTemplate jdbcTemplate = new CountingJdbcTemplate();
        CatalogAssetTagPermissionReadAdapter adapter = new CatalogAssetTagPermissionReadAdapter(jdbcTemplate);
        Set<String> apiCodes = IntStream
            .range(0, 500)
            .mapToObj(index -> "api_" + index)
            .collect(Collectors.toSet());
        Map<CatalogAssetType, Set<String>> naturalKeys = new LinkedHashMap<>();
        naturalKeys.put(CatalogAssetType.API_SERVICE, apiCodes);

        adapter.loadBatch(new BatchLookupRequest(Set.of(), Set.of(), naturalKeys));

        assertThat(jdbcTemplate.queryCount()).isEqualTo(1);
        QueryInvocation query = jdbcTemplate.singleQuery();
        assertThat(query.sql().toLowerCase(Locale.ROOT))
            .contains(
                "requested_natural_key",
                "row_number() over",
                "partition by requested.natural_key",
                "where match_rank <= 2",
                "fetch first ? rows only"
            );
        assertThat(query.arguments()).contains(1001);
    }

    @Test
    void semanticModelIdentityUsesCanonicalModelSpecTable() {
        CountingJdbcTemplate jdbcTemplate = new CountingJdbcTemplate();
        CatalogAssetTagPermissionReadAdapter adapter = new CatalogAssetTagPermissionReadAdapter(jdbcTemplate);

        adapter.loadBatch(
            new BatchLookupRequest(
                Set.of(),
                Set.of(),
                Map.of(
                    CatalogAssetType.SEMANTIC_MODEL,
                    Set.of("00000000-0000-0000-0000-000000000004")
                )
            )
        );

        assertThat(jdbcTemplate.singleQuery().sql().toLowerCase(Locale.ROOT))
            .contains("from modeling_model_spec")
            .doesNotContain("from semantic_model");
    }

    @Test
    void localMetricIdentityUsesGovernanceIndicatorTable() {
        CountingJdbcTemplate jdbcTemplate = new CountingJdbcTemplate();
        CatalogAssetTagPermissionReadAdapter adapter = new CatalogAssetTagPermissionReadAdapter(jdbcTemplate);

        adapter.loadBatch(
            new BatchLookupRequest(
                Set.of(),
                Set.of(),
                Map.of(CatalogAssetType.METRIC, Set.of("metric:local/revenue"))
            )
        );

        List<QueryInvocation> queries = jdbcTemplate.queries();
        assertThat(queries)
            .extracting(query -> query.sql().toLowerCase(Locale.ROOT))
            .anySatisfy(sql ->
                assertThat(sql)
                    .contains("from gov_indicator_definition")
                    .doesNotContain("from semantic_metric")
            );
    }

    @Test
    void legacyDatasetLookupPushesCanonicalSchemaAndTableIntoOneBoundedQuery() {
        CountingJdbcTemplate jdbcTemplate = new CountingJdbcTemplate();
        CatalogAssetTagPermissionReadAdapter adapter = new CatalogAssetTagPermissionReadAdapter(jdbcTemplate);
        String key =
            "source:" +
            SOURCE_ID +
            "/schema:dwd/table:order_detail";

        adapter.loadBatch(
            new BatchLookupRequest(Set.of(key), Set.of(), Map.of())
        );

        QueryInvocation query = jdbcTemplate.singleQuery();
        assertThat(query.sql().toLowerCase(Locale.ROOT))
            .contains(
                "requested_legacy_dataset",
                "r.schema_name",
                "r.table_name",
                "fetch first ? rows only"
            )
            .doesNotContain("andregexp");
        assertThat(query.arguments())
            .contains(SOURCE_ID, "dwd", "order_detail", 3);
    }

    @Test
    void fiveHundredOpenMetadataKeysUseOneBoundedSetQueryWithoutLikeFanOut() {
        CountingJdbcTemplate jdbcTemplate = new CountingJdbcTemplate();
        CatalogAssetTagPermissionReadAdapter adapter = new CatalogAssetTagPermissionReadAdapter(jdbcTemplate);
        Set<String> keys = IntStream
            .range(0, 500)
            .mapToObj(index -> "om:service.db.schema.table_" + index)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        adapter.loadBatch(
            new BatchLookupRequest(Set.of(), keys, Map.of())
        );

        QueryInvocation query = jdbcTemplate.singleQuery();
        assertThat(jdbcTemplate.queryCount()).isEqualTo(1);
        assertThat(query.sql().toLowerCase(Locale.ROOT))
            .contains(
                "requested_openmetadata_dataset",
                "fetch first ? rows only"
            )
            .doesNotContain(" like ", "onregexp");
        assertThat(query.arguments()).contains(1001);
    }

    @Test
    void openMetadataUnderscoresRemainLiteralLookupValues() {
        CountingJdbcTemplate jdbcTemplate = new CountingJdbcTemplate();
        CatalogAssetTagPermissionReadAdapter adapter = new CatalogAssetTagPermissionReadAdapter(jdbcTemplate);

        adapter.loadBatch(
            new BatchLookupRequest(
                Set.of(),
                Set.of("om:service.db.schema.table_name"),
                Map.of()
            )
        );

        assertThat(jdbcTemplate.singleQuery().arguments())
            .contains("service.db.schema.table_name")
            .noneMatch(argument ->
                argument instanceof String value && value.contains("%")
            );
    }

    private static final class CountingJdbcTemplate extends JdbcTemplate {

        private final List<QueryInvocation> queries = new ArrayList<>();

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queries.add(new QueryInvocation(sql, args.clone()));
            return List.of();
        }

        int queryCount() {
            return queries.size();
        }

        QueryInvocation singleQuery() {
            return queries.getFirst();
        }

        List<QueryInvocation> queries() {
            return List.copyOf(queries);
        }
    }

    private record QueryInvocation(String sql, Object[] arguments) {}
}
