package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetAssetRepository;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetVersionRepository;
import com.yuzhi.dts.platform.service.etl.DbtFileService;
import com.yuzhi.dts.platform.service.etl.DbtReleaseSubmissionService;
import com.yuzhi.dts.platform.service.query.QueryGateway;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class SemanticModelingServiceTest {

    private NamedParameterJdbcTemplate jdbc;
    private SemanticModelingService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        stubQuery();
        service =
            new SemanticModelingService(
                jdbc,
                new ObjectMapper(),
                mock(DbtFileService.class),
                mock(QueryGateway.class),
                mock(QueryDatasetAssetRepository.class),
                mock(QueryDatasetVersionRepository.class),
                mock(CatalogDatasetRepository.class),
                mock(CatalogDatasetLineageRepository.class),
                mock(DbtReleaseSubmissionService.class)
            );
    }

    @Test
    void listBusinessObjectsOmitsDomainFilterWhenDomainIdIsNull() {
        service.listBusinessObjects(null);

        CapturedQuery query = captureQuery();
        assertThat(query.sql()).contains("from semantic_business_object").doesNotContain("domain_id = :domainId");
        assertThat(query.params().getValues()).doesNotContainKey("domainId");
    }

    @Test
    void listMetricsOmitsObjectFilterWhenObjectIdIsNull() {
        service.listMetrics(null);

        CapturedQuery query = captureQuery();
        assertThat(query.sql()).contains("from semantic_metric").doesNotContain("object_id = :objectId");
        assertThat(query.params().getValues()).doesNotContainKey("objectId");
    }

    @Test
    void listDimensionsAndArtifactsKeepUuidFiltersWhenPresent() {
        UUID objectId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();

        service.listDimensions(objectId);
        CapturedQuery dimensionsQuery = captureQuery();
        assertThat(dimensionsQuery.sql()).contains("where object_id = :objectId");
        assertThat(dimensionsQuery.params().getValue("objectId")).isEqualTo(objectId);

        service.listGeneratedArtifacts(modelId);
        CapturedQuery artifactsQuery = captureQuery();
        assertThat(artifactsQuery.sql()).contains("where model_id = :modelId");
        assertThat(artifactsQuery.params().getValue("modelId")).isEqualTo(modelId);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void stubQuery() {
        doReturn(List.of()).when(jdbc).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private CapturedQuery captureQuery() {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        clearInvocations(jdbc);
        return new CapturedQuery(sqlCaptor.getValue(), paramsCaptor.getValue());
    }

    private record CapturedQuery(String sql, MapSqlParameterSource params) {}
}
