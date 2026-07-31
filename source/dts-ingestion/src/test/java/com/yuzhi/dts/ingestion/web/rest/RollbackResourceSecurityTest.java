package com.yuzhi.dts.ingestion.web.rest;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.ingestion.service.etl.rollback.DataRollbackService;
import com.yuzhi.dts.ingestion.service.etl.rollback.RollbackAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;

@WebMvcTest(RollbackResource.class)
@TestPropertySource(properties = "dts.ingestion.trusted-service-token=platform-to-ingestion-test-token")
class RollbackResourceSecurityTest {

    private static final String TASK_REQUEST = """
        {"level":1,"scope":"task","taskId":41,"tables":[],"rebuildDbt":false,"dryRun":true}
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
    @WithMockUser(authorities = "ROLE_ADMIN")
    void executeMustRejectDryRunBeforeCallingRollbackService() throws Exception {
        mockMvc.perform(post("/api/ingestion/rollback/execute")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(TASK_REQUEST))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(rollbackService, auditService);
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
            .andExpect(status().isForbidden());

        verifyNoInteractions(rollbackService, auditService);
    }
}
