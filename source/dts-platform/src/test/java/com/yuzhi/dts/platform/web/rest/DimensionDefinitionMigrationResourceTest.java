package com.yuzhi.dts.platform.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.security.session.PortalSessionInactivityFilter;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.migration.DimensionDefinitionMigrationService;
import com.yuzhi.dts.platform.service.modeling.migration.DimensionDefinitionMigrationService.DryRunReport;
import com.yuzhi.dts.platform.service.modeling.migration.DimensionDefinitionMigrationService.MigrationExecution;
import com.yuzhi.dts.platform.service.modeling.migration.DimensionDefinitionMigrationService.MigrationRollback;
import com.yuzhi.dts.platform.service.modeling.migration.DimensionDefinitionMigrationService.MigrationStatus;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.web.filter.AuditLoggingFilter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    value = DimensionDefinitionMigrationResource.class,
    excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration",
        "dts.platform.modeling.default-tenant-id=server-tenant",
    }
)
@AutoConfigureMockMvc(addFilters = false)
class DimensionDefinitionMigrationResourceTest {

    private static final String CHECKSUM = "a".repeat(64);
    private static final String BATCH = "dimension-definitions-aaaaaaaaaaaaaaaa";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DimensionDefinitionMigrationService service;

    @MockBean
    private AuditService audit;

    @MockBean
    private WarehousePlanActorProvider actorProvider;

    @MockBean
    private PortalSessionInactivityFilter portalSessionInactivityFilter;

    @MockBean
    private AuditLoggingFilter auditLoggingFilter;

    @Test
    void usesOnlyServerTenantAndAuthenticatedActorForDryRunAndExecute() throws Exception {
        DryRunReport report = new DryRunReport(BATCH, CHECKSUM, 2, 1, 1, 0, 0, Map.of(), List.of());
        MigrationExecution execution = new MigrationExecution(BATCH, CHECKSUM, false, 2, 1, 1, 2, Map.of());
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", "dept-a"));
        when(service.dryRun("server-tenant", "alice")).thenReturn(report);
        when(service.execute("server-tenant", BATCH, CHECKSUM, "alice")).thenReturn(execution);

        mockMvc
            .perform(post("/api/modeling/migrations/dimension-definitions/dry-run").header("X-Tenant-Id", "client-tenant"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.batchId").value(BATCH));

        mockMvc
            .perform(
                post("/api/modeling/migrations/dimension-definitions/{batchId}/execute", BATCH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedChecksum\":\"" + CHECKSUM + "\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mappingsCreated").value(2));

        verify(service).dryRun("server-tenant", "alice");
        verify(service).execute("server-tenant", BATCH, CHECKSUM, "alice");
    }

    @Test
    void rejectsExtraOrMalformedExecuteFieldsBeforeCallingTheService() throws Exception {
        mockMvc
            .perform(
                post("/api/modeling/migrations/dimension-definitions/{batchId}/execute", BATCH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedChecksum\":\"" + CHECKSUM + "\",\"tenantId\":\"forbidden\"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MIGRATION_REQUEST_INVALID"));

        mockMvc
            .perform(
                post("/api/modeling/migrations/dimension-definitions/{batchId}/execute", BATCH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedChecksum\":123}")
            )
            .andExpect(status().isBadRequest());

        verify(service, never()).execute(any(), any(), any(), any());
    }

    @Test
    void mapsDriftRejectionToTheLegacyMigrationConflictEnvelope() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", null));
        when(service.execute(eq("server-tenant"), eq(BATCH), eq(CHECKSUM), eq("alice")))
            .thenThrow(new IllegalStateException("DIMENSION_MIGRATION_BATCH_STALE: dry-run batch identity changed"));

        mockMvc
            .perform(
                post("/api/modeling/migrations/dimension-definitions/{batchId}/execute", BATCH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedChecksum\":\"" + CHECKSUM + "\"}")
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MIGRATION_GATE_BLOCKED"));
    }

    @Test
    void rollsBackOnlyThroughServerTenantActorAndWritesAnAuditResult() throws Exception {
        MigrationRollback rollback = new MigrationRollback(
            BATCH,
            CHECKSUM,
            false,
            2,
            1,
            0,
            MigrationStatus.ROLLED_BACK
        );
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", "dept-a"));
        when(service.rollback("server-tenant", BATCH, CHECKSUM, "alice")).thenReturn(rollback);

        mockMvc
            .perform(
                post("/api/modeling/migrations/dimension-definitions/{batchId}/rollback", BATCH)
                    .header("X-Tenant-Id", "forged-tenant")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedChecksum\":\"" + CHECKSUM + "\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ROLLED_BACK"))
            .andExpect(jsonPath("$.data.mappingsDeleted").value(2))
            .andExpect(jsonPath("$.data.definitionsDeleted").value(1));

        verify(service).rollback("server-tenant", BATCH, CHECKSUM, "alice");
        verify(audit).auditAction(
            eq("DIMENSION_DEFINITION_MIGRATION_ROLLBACK"),
            any(),
            eq(BATCH),
            any()
        );
    }
}
