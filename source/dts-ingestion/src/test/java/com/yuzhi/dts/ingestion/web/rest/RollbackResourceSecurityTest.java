package com.yuzhi.dts.ingestion.web.rest;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.yuzhi.dts.ingestion.service.etl.rollback.DataRollbackService;
import com.yuzhi.dts.ingestion.service.etl.rollback.RollbackAuditService;
import com.yuzhi.dts.ingestion.service.etl.rollback.RollbackResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;

@WebMvcTest(RollbackResource.class)
@TestPropertySource(
    properties = "dts.ingestion.trusted-service-tokens.dts-platform=platform-to-ingestion-test-token"
)
@Import(RollbackResourceSecurityTest.MethodSecurityTestConfiguration.class)
class RollbackResourceSecurityTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityTestConfiguration {}

    private static final String TASK_REQUEST = """
        {"level":1,"scope":"task","taskId":41,"tables":[],"dryRun":true}
        """;
    private static final String FENCED_EXECUTE_REQUEST = """
        {
          "level":3,
          "scope":"task",
          "taskId":41,
          "tables":[],
          "dryRun":false,
          "rollbackId":"22222222-2222-2222-2222-222222222222",
          "idempotencyKey":"platform:22222222-2222-2222-2222-222222222222",
          "requestHash":"0000000000000000000000000000000000000000000000000000000000000000",
          "availabilityFence":{
            "receiptId":"22222222-2222-2222-2222-222222222222",
            "sourceDataSourceId":"11111111-1111-1111-1111-111111111111",
            "sourceSequence":7,
            "state":"PREPARED",
            "targetCount":1
          }
        }
        """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DataRollbackService rollbackService;

    @MockBean
    private RollbackAuditService auditService;

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void nonMaintainerMustReceiveForbiddenForEveryRollbackEndpoint() throws Exception {
        mockMvc.perform(post("/api/ingestion/rollback/analyze")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(TASK_REQUEST))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ingestion/rollback/execute")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(TASK_REQUEST))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ingestion/rollback/audit-log").param("taskId", "41"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(rollbackService, auditService);
    }

    @Test
    @WithMockUser(authorities = { "ROLE_ADMIN", "ROLE_SERVICE_DTS_PLATFORM" })
    void executeMustRejectDryRunBeforeCallingRollbackService() throws Exception {
        mockMvc.perform(post("/api/ingestion/rollback/execute")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(TASK_REQUEST))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("请求参数错误"))
            .andExpect(jsonPath("$.code").value("HTTP_400"));

        verifyNoInteractions(rollbackService, auditService);
    }

    @Test
    @WithMockUser(authorities = { "ROLE_ADMIN", "ROLE_SERVICE_DTS_PLATFORM" }, username = "rollback-admin")
    void executeMustReturnNon2xxWhenRollbackIsIncomplete() throws Exception {
        when(rollbackService.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("rollback-admin")))
            .thenReturn(new RollbackResult(
                false,
                List.of("DROPPED: public.orders"),
                List.of("DAG_DELETE_FAILED: timeout"),
                List.of(41L)
            ));

        mockMvc.perform(post("/api/ingestion/rollback/execute")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(FENCED_EXECUTE_REQUEST))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ROLLBACK_INCOMPLETE"))
            .andExpect(jsonPath("$.data.success").value(false));
    }

    @Test
    void trustedServiceNameAndForwardedAdminWithoutPairwiseTokenMustReceiveForbidden() throws Exception {
        mockMvc.perform(post("/api/ingestion/rollback/analyze")
                .with(csrf())
                .header("X-DTS-Service", "dts-platform")
                .header("X-DTS-User", "forged-admin")
                .header("X-DTS-Roles", "ROLE_ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(TASK_REQUEST))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(rollbackService, auditService);
    }

    @Test
    @WithMockUser(
        authorities = { "ROLE_OP_ADMIN", "ROLE_SERVICE_DTS_PLATFORM" },
        username = "service:dts-platform"
    )
    void machinePrincipalWithoutForwardedUserMustNotInvokeRollback() throws Exception {
        mockMvc.perform(post("/api/ingestion/rollback/execute")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(FENCED_EXECUTE_REQUEST))
            .andExpect(status().isForbidden());

        verifyNoInteractions(rollbackService, auditService);
    }

    @Test
    @WithMockUser(authorities = { "ROLE_ADMIN", "ROLE_SERVICE_DTS_PLATFORM" }, username = "rollback-admin")
    void unknownOrOversizedCommandFieldsMustFailBeforeServiceInvocation() throws Exception {
        mockMvc.perform(post("/api/ingestion/rollback/analyze")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"level":1,"scope":"task","taskId":41,"tables":[],"dryRun":true,"unknown":true}
                    """))
            .andExpect(status().isBadRequest());

        String oversizedTables = java.util.stream.IntStream.range(0, 501)
            .mapToObj(index -> "\"public.table_" + index + "\"")
            .collect(java.util.stream.Collectors.joining(","));
        mockMvc.perform(post("/api/ingestion/rollback/analyze")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"level\":1,\"scope\":\"task\",\"taskId\":41,\"tables\":[" +
                    oversizedTables + "],\"dryRun\":true}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(rollbackService, auditService);
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN", username = "direct-admin")
    void directJwtAdminCannotForgeAPlatformFence() throws Exception {
        mockMvc.perform(post("/api/ingestion/rollback/execute")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(FENCED_EXECUTE_REQUEST))
            .andExpect(status().isForbidden());

        verifyNoInteractions(rollbackService, auditService);
    }
}
