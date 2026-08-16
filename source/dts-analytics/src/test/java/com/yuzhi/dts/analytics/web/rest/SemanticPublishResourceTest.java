package com.yuzhi.dts.analytics.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.analytics.domain.AnalyticsSemanticModel;
import com.yuzhi.dts.analytics.domain.AnalyticsTable;
import com.yuzhi.dts.analytics.repository.AnalyticsDatabaseRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsFieldRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsMetricRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsSemanticJoinRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsSemanticModelRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsTableRepository;
import com.yuzhi.dts.analytics.service.AnalyticsConsumerClassificationService;
import com.yuzhi.dts.analytics.service.SemanticAuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SemanticPublishResourceTest {

    @Test
    void resolvesDatabaseByStablePlatformDataSourceIdBeforeNameFallback() {
        AnalyticsDatabaseRepository databases = mock(AnalyticsDatabaseRepository.class);
        AnalyticsTableRepository tables = mock(AnalyticsTableRepository.class);
        AnalyticsFieldRepository fields = mock(AnalyticsFieldRepository.class);
        AnalyticsMetricRepository metrics = mock(AnalyticsMetricRepository.class);
        AnalyticsSemanticModelRepository models = mock(AnalyticsSemanticModelRepository.class);
        AnalyticsSemanticJoinRepository joins = mock(AnalyticsSemanticJoinRepository.class);
        SemanticAuditService audits = mock(SemanticAuditService.class);
        AnalyticsConsumerClassificationService classifications = mock(AnalyticsConsumerClassificationService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AnalyticsDatabase other = database(1L, "其他库", "90000000-0000-0000-0000-000000000001");
        AnalyticsDatabase target = database(2L, "数据湖 (数仓)", "10000000-0000-0000-0000-000000000001");
        AnalyticsTable table = new AnalyticsTable();
        table.setId(20L);
        table.setDatabaseId(2L);
        table.setSchemaName("public");
        table.setName("pjm_dws_budget_execution");
        AnalyticsSemanticModel model = new AnalyticsSemanticModel();
        model.setModelName("model_spec_30000000000000000000000000000001");
        when(databases.findAll()).thenReturn(List.of(other, target));
        when(tables.findByDatabaseIdAndSchemaNameAndName(2L, "public", "pjm_dws_budget_execution"))
            .thenReturn(Optional.of(table));
        when(models.findByModelNameIgnoreCase("model_spec_30000000000000000000000000000001"))
            .thenReturn(Optional.of(model));
        when(models.save(model)).thenReturn(model);
        SemanticPublishResource resource = new SemanticPublishResource(
            databases,
            tables,
            fields,
            metrics,
            models,
            joins,
            objectMapper,
            audits,
            classifications
        );
        var body = objectMapper.createObjectNode();
        body.put("platformDataSourceId", "10000000-0000-0000-0000-000000000001");
        body.put("modelName", "model_spec_30000000000000000000000000000001");
        body.put("tableName", "pjm_dws_budget_execution");
        body.put("schemaName", "public");

        var response = resource.publish(body, mock(HttpServletRequest.class));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(tables).findByDatabaseIdAndSchemaNameAndName(2L, "public", "pjm_dws_budget_execution");
    }

    private static AnalyticsDatabase database(Long id, String name, String platformSourceId) {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(id);
        database.setName(name);
        database.setEngine("postgres");
        database.setDetailsJson("{\"platformDataSourceId\":\"" + platformSourceId + "\"}");
        database.setSample(false);
        return database;
    }
}
