package com.yuzhi.dts.analytics.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import java.util.UUID;
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
        model.setDatabaseId(2L);
        model.setTableId(20L);
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

    @Test
    void rejectsSameNameFromAnotherTenantWithoutChangingExistingSemanticBinding() {
        AnalyticsTableRepository tables = mock(AnalyticsTableRepository.class);
        AnalyticsFieldRepository fields = mock(AnalyticsFieldRepository.class);
        AnalyticsMetricRepository metrics = mock(AnalyticsMetricRepository.class);
        AnalyticsSemanticModelRepository models = mock(AnalyticsSemanticModelRepository.class);
        AnalyticsSemanticJoinRepository joins = mock(AnalyticsSemanticJoinRepository.class);
        SemanticAuditService audits = mock(SemanticAuditService.class);
        AnalyticsConsumerClassificationService classifications = mock(AnalyticsConsumerClassificationService.class);
        PlatformAnalyticsDatabaseRegistrationService registration = mock(PlatformAnalyticsDatabaseRegistrationService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AnalyticsDatabase tenantBTarget = database(2L, "tenant-b", "20000000-0000-0000-0000-000000000002", "tenant-b");
        AnalyticsTable tenantBTable = new AnalyticsTable();
        tenantBTable.setId(20L);
        tenantBTable.setDatabaseId(2L);
        tenantBTable.setSchemaName("public");
        tenantBTable.setName("orders");
        AnalyticsSemanticModel existing = new AnalyticsSemanticModel();
        existing.setDatabaseId(1L);
        existing.setTableId(10L);
        existing.setModelName("orders_semantic");
        when(models.findByModelNameIgnoreCase("orders_semantic")).thenReturn(Optional.of(existing));
        when(registration.ensureTarget(org.mockito.ArgumentMatchers.eq("tenant-b"), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq("public"), org.mockito.ArgumentMatchers.eq("orders"), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new PlatformAnalyticsDatabaseRegistrationService.TargetRegistration(tenantBTarget, tenantBTable, java.util.Set.of("order_id")));
        SemanticPublishResource resource = new SemanticPublishResource(
            tables, fields, metrics, models, joins, objectMapper, audits, classifications, registration
        );
        var body = objectMapper.createObjectNode();
        body.put("platformDataSourceId", "20000000-0000-0000-0000-000000000002");
        body.put("tenantId", "tenant-b");
        body.put("modelName", "orders_semantic");
        body.put("tableName", "orders");
        body.put("schemaName", "public");

        var response = resource.publish(body, mock(HttpServletRequest.class));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isEqualTo(java.util.Map.of("code", "ANALYSIS_SEMANTIC_MODEL_BINDING_CONFLICT"));
        assertThat(existing.getDatabaseId()).isEqualTo(1L);
        assertThat(existing.getTableId()).isEqualTo(10L);
        verify(models, never()).save(existing);
        verify(metrics, never()).findAll();
        verify(joins, never()).findAll();
    }

    private static AnalyticsDatabase database(Long id, String name, String platformSourceId) {
        return database(id, name, platformSourceId, "default");
    }

    private static AnalyticsDatabase database(Long id, String name, String platformSourceId, String tenantId) {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(id);
        database.setName(name);
        database.setTenantId(tenantId);
        database.setPlatformDataSourceId(UUID.fromString(platformSourceId));
        database.setEngine("postgres");
        database.setDetailsJson("{\"platformDataSourceId\":\"" + platformSourceId + "\"}");
        database.setSample(false);
        return database;
    }
}
