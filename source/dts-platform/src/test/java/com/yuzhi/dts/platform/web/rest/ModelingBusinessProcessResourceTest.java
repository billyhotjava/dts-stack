package com.yuzhi.dts.platform.web.rest;

import static com.yuzhi.dts.platform.service.modeling.BusinessProcessApplicationService.AuditSurface.CANONICAL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.security.session.PortalSessionInactivityFilter;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.BusinessProcessApplicationService;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusinessProcessDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    value = ModelingBusinessProcessResource.class,
    excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration",
    }
)
@AutoConfigureMockMvc(addFilters = false)
class ModelingBusinessProcessResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BusinessProcessApplicationService service;

    @MockBean
    private AuditService auditService;

    @MockBean
    private PortalSessionInactivityFilter portalSessionInactivityFilter;

    @MockBean
    private com.yuzhi.dts.platform.web.filter.DbtImplementationDraftBodyLimitFilter dbtImplementationDraftBodyLimitFilter;

    @MockBean
    private com.yuzhi.dts.platform.web.filter.DimensionModelBodyLimitFilter dimensionModelBodyLimitFilter;

    @MockBean
    private com.yuzhi.dts.platform.web.filter.AuditLoggingFilter auditLoggingFilter;

    private static final UUID DOMAIN = UUID.fromString("40000000-0000-0000-0000-000000000001");

    @Test
    void updatesTheExistingProcessThroughPut() throws Exception {
        when(service.update(eq(DOMAIN), eq("budget"), any())).thenReturn(process("budget"));
        mockMvc.perform(put("/api/modeling/business-processes/budget")
                .param("domainId", DOMAIN.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"预算管理修订\",\"description\":\"更新定义\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.processId").value("budget"));
        org.mockito.Mockito.verify(service).update(
            DOMAIN, "budget",
            new com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusinessProcessUpdateRequest("预算管理修订", "更新定义")
        );
    }

    @Test
    void listsProcessesForADomain() throws Exception {
        when(service.list(DOMAIN, CANONICAL)).thenReturn(List.of(process("BUDGET")));

        mockMvc
            .perform(get("/api/modeling/business-processes").param("domainId", DOMAIN.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].processId").value("BUDGET"));
    }

    @Test
    void createsProcessOnTheCanonicalResource() throws Exception {
        when(service.create(eq(DOMAIN), any(), eq(CANONICAL))).thenReturn(process("BUDGET"));

        mockMvc
            .perform(post("/api/modeling/business-processes")
                .param("domainId", DOMAIN.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"processId":"budget","name":"预算管理","description":"预算执行过程"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.processId").value("BUDGET"));
    }

    @Test
    void deletesProcessFromTheCanonicalResource() throws Exception {
        mockMvc
            .perform(delete("/api/modeling/business-processes/budget").param("domainId", DOMAIN.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void mapsValidationFailuresToBadRequestWithStableCode() throws Exception {
        doThrow(new IllegalArgumentException("processId 不能为空"))
            .when(service)
            .create(eq(DOMAIN), any(), eq(CANONICAL));

        mockMvc
            .perform(post("/api/modeling/business-processes")
                .param("domainId", DOMAIN.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"processId":"","name":"预算管理"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BUSINESS_PROCESS_INVALID"));
    }

    @Test
    void mapsDuplicateProcessToConflictWithStableCode() throws Exception {
        doThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint uk_sprint64_process_domain_key"))
            .when(service)
            .create(eq(DOMAIN), any(), eq(CANONICAL));

        mockMvc
            .perform(post("/api/modeling/business-processes")
                .param("domainId", DOMAIN.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"processId":"budget","name":"预算管理"}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("BUSINESS_PROCESS_DUPLICATE"));
    }

    private static BusinessProcessDto process(String processId) {
        Instant now = Instant.parse("2026-08-06T12:00:00Z");
        return new BusinessProcessDto(
            UUID.randomUUID(),
            1,
            processId,
            DOMAIN,
            "预算管理",
            "预算执行过程",
            "MANUAL",
            null,
            null,
            true,
            now,
            now
        );
    }
}
