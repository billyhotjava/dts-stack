package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.catalog.CatalogAssetDirectoryReadAdapter.AssetRelationship;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

class CatalogAssetDirectoryReadAdapterTest {

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    void excludesDisabledScreensFromTheAssetDirectory() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        CatalogAssetDirectoryReadAdapter adapter = new CatalogAssetDirectoryReadAdapter(jdbcTemplate);

        adapter.load(Set.of(CatalogAssetType.SCREEN));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getValue()).contains("and enabled = true");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    void boundsOwnerQueriesWithOneOverflowSentinelAcrossTheRequestedFamilies() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        CatalogAssetDirectoryReadAdapter adapter = new CatalogAssetDirectoryReadAdapter(jdbcTemplate);

        adapter.load(
            Set.of(
                CatalogAssetType.SEMANTIC_MODEL,
                CatalogAssetType.GOV_INDICATOR,
                CatalogAssetType.BI_DATASET,
                CatalogAssetType.SCREEN,
                CatalogAssetType.DATA_PRODUCT,
                CatalogAssetType.API_SERVICE
            )
        );

        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(6)).query(anyString(), any(RowMapper.class), arguments.capture());
        assertThat(arguments.getAllValues()).allSatisfy(values -> assertThat(values).containsExactly(5_001));
    }

    @Test
    void resolvesMaterializedDatasetThroughTheServingPhysicalAssetObservation() throws Exception {
        UUID modelId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID datasetId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        String modelKey = CatalogAssetKey.semanticModel(modelId.toString());
        String datasetKey = "source:unknown/schema:dwd/table:project_fact";
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (!sql.contains("modeling_catalog_model_serving_projection")) {
                return null;
            }
            ResultSet row = mock(ResultSet.class);
            when(row.getString("model_spec_id")).thenReturn(modelId.toString());
            when(row.getString("dataset_asset_key")).thenReturn(datasetKey);
            when(row.getString("model_name")).thenReturn("项目事实模型");
            when(row.getString("dataset_name")).thenReturn("项目事实表");
            when(row.getString("dataset_id")).thenReturn(datasetId.toString());
            RowCallbackHandler callback = invocation.getArgument(1);
            callback.processRow(row);
            callback.processRow(row);
            return null;
        })
            .when(jdbcTemplate)
            .query(anyString(), any(RowCallbackHandler.class));
        CatalogAssetDirectoryReadAdapter adapter = new CatalogAssetDirectoryReadAdapter(jdbcTemplate);

        Map<AssetRef, List<AssetRelationship>> relationships = adapter.loadRelationships(
            List.of(new AssetRef(CatalogAssetType.SEMANTIC_MODEL.name(), modelKey))
        );

        assertThat(relationships.get(new AssetRef(CatalogAssetType.SEMANTIC_MODEL.name(), modelKey)))
            .singleElement()
            .satisfies(relation -> {
                assertThat(relation.relationType()).isEqualTo("MATERIALIZES_TO");
                assertThat(relation.assetType()).isEqualTo(CatalogAssetType.DATASET.name());
                assertThat(relation.assetKey()).isEqualTo(datasetKey);
                assertThat(relation.displayName()).isEqualTo("项目事实表");
                assertThat(relation.detailRoute()).isEqualTo("/catalog/datasets/" + datasetId);
            });

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).query(sqlCaptor.capture(), any(RowCallbackHandler.class));
        String materializationSql = sqlCaptor
            .getAllValues()
            .stream()
            .filter(sql -> sql.contains("modeling_catalog_model_serving_projection"))
            .findFirst()
            .orElseThrow();
        assertThat(materializationSql)
            .contains("join catalog_asset_semantic_projection")
            .contains("serving_ref ->> 'physicalAssetId'")
            .doesNotContain("upper(coalesce(p.catalog_asset_type, '')) = 'DATASET'");
    }
}
