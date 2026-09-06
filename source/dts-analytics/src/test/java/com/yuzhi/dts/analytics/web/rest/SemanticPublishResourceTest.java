package com.yuzhi.dts.analytics.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.analytics.domain.AnalyticsSemanticModel;
import com.yuzhi.dts.analytics.domain.AnalyticsTable;
import com.yuzhi.dts.analytics.repository.AnalyticsFieldRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsMetricRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsSemanticJoinRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsSemanticModelRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsTableRepository;
import com.yuzhi.dts.analytics.service.AnalyticsConsumerClassificationService;
import com.yuzhi.dts.analytics.service.PlatformAnalyticsDatabaseRegistrationService;
import com.yuzhi.dts.analytics.service.SemanticAuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SemanticPublishResourceTest {

    @Test
    void publishesOnlyAfterTheStableTenantSourceTargetRegistrationSucceeds() {
        AnalyticsTableRepository tables = mock(AnalyticsTableRepository.class);
        AnalyticsFieldRepository fields = mock(AnalyticsFieldRepository.class);
        AnalyticsMetricRepository metrics = mock(AnalyticsMetricRepository.class);
        AnalyticsSemanticModelRepository models = mock(AnalyticsSemanticModelRepository.class);
        AnalyticsSemanticJoinRepository joins = mock(AnalyticsSemanticJoinRepository.class);
        SemanticAuditService audits = mock(SemanticAuditService.class);
        AnalyticsConsumerClassificationService classifications = mock(AnalyticsConsumerClassificationService.class);
        PlatformAnalyticsDatabaseRegistrationService registration = mock(PlatformAnalyticsDatabaseRegistrationService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AnalyticsDatabase target = database(2L, "数据湖 (数仓)", "10000000-0000-0000-0000-000000000001");
        AnalyticsTable table = new AnalyticsTable();
        table.setId(20L);
        table.setDatabaseId(2L);
        table.setSchemaName("public");
        table.setName("pjm_dws_budget_execution");
        AnalyticsSemanticModel model = new AnalyticsSemanticModel();
        model.setModelName("model_spec_30000000000000000000000000000001");
        when(models.findByModelNameIgnoreCase("model_spec_30000000000000000000000000000001"))
            .thenReturn(Optional.of(model));
        when(models.save(model)).thenReturn(model);
        SemanticPublishResource resource = new SemanticPublishResource(
            tables,
            fields,
            metrics,
            models,
            joins,
            objectMapper,
            audits,
            classifications,
            registration
        );
        var body = objectMapper.createObjectNode();
        body.put("platformDataSourceId", "10000000-0000-0000-0000-000000000001");
        body.put("tenantId", "default");
        body.put("modelName", "model_spec_30000000000000000000000000000001");
        body.put("tableName", "pjm_dws_budget_execution");
        body.put("schemaName", "public");

        when(registration.ensureTarget(org.mockito.ArgumentMatchers.eq("default"), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq("public"), org.mockito.ArgumentMatchers.eq("pjm_dws_budget_execution"), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new PlatformAnalyticsDatabaseRegistrationService.TargetRegistration(target, table, java.util.Set.of("project_code")));
        var response = resource.publish(body, mock(HttpServletRequest.class));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(registration).ensureTarget(org.mockito.ArgumentMatchers.eq("default"), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq("public"), org.mockito.ArgumentMatchers.eq("pjm_dws_budget_execution"), org.mockito.ArgumentMatchers.any());
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
