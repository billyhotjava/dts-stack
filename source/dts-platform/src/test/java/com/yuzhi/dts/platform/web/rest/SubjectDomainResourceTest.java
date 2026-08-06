package com.yuzhi.dts.platform.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.security.session.PortalSessionInactivityFilter;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.SubjectDomainApplicationService;
import com.yuzhi.dts.platform.service.modeling.SubjectDomainApplicationService.CreateResult;
import com.yuzhi.dts.platform.service.modeling.SubjectDomainContract.Status;
import com.yuzhi.dts.platform.service.modeling.SubjectDomainContract.View;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    value = SubjectDomainResource.class,
    excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration",
    }
)
@AutoConfigureMockMvc(addFilters = false)
class SubjectDomainResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubjectDomainApplicationService service;

    @MockBean
    private WarehousePlanActorProvider actorProvider;

    @MockBean
    private PortalSessionInactivityFilter portalSessionInactivityFilter;

    @MockBean
    private com.yuzhi.dts.platform.web.filter.DbtImplementationDraftBodyLimitFilter dbtImplementationDraftBodyLimitFilter;

    @MockBean
    private com.yuzhi.dts.platform.web.filter.DimensionModelBodyLimitFilter dimensionModelBodyLimitFilter;

    @MockBean
    private com.yuzhi.dts.platform.service.audit.AuditService auditService;

    @MockBean
    private com.yuzhi.dts.platform.web.filter.AuditLoggingFilter auditLoggingFilter;

    private static final UUID MART = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void listsSubjectDomains() throws Exception {
        when(service.list(eq("default"), eq(MART), eq(Status.CURRENT), eq(null), eq(0), eq(10)))
            .thenReturn(List.of(view("BUDGET_COCKPIT", "预算驾驶舱", Status.CURRENT)));

        mockMvc
            .perform(get("/api/modeling/subject-domains").param("martId", MART.toString()).param("status", "CURRENT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].code").value("BUDGET_COCKPIT"))
            .andExpect(jsonPath("$.data[0].status").value("CURRENT"));
    }

    @Test
    void createsSubjectDomainAndReturnsLocation() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", "department"));
        View created = view("BUDGET_COCKPIT", "预算驾驶舱", Status.DRAFT);
        when(service.create(eq("default"), eq("alice"), any()))
            .thenReturn(new CreateResult(created, false));

        mockMvc
            .perform(post("/api/modeling/subject-domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"code":"BUDGET_COCKPIT","name":"预算驾驶舱","purpose":"预算分析主题",
                     "martId":"%s","idempotencyKey":"create-budget-cockpit"}
                    """.formatted(MART)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/modeling/subject-domains/" + created.id()))
            .andExpect(jsonPath("$.data.code").value("BUDGET_COCKPIT"));
    }

    @Test
    void updateRequiresStrongIfMatch() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", "department"));

        mockMvc
            .perform(put("/api/modeling/subject-domains/" + MART)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"预算驾驶舱","purpose":"预算分析主题","martId":"%s"}
                    """.formatted(MART)))
            .andExpect(status().isPreconditionRequired())
            .andExpect(jsonPath("$.code").value("SUBJECT_DOMAIN_IF_MATCH_REQUIRED"));
        verify(service, org.mockito.Mockito.never()).update(any(), any(), any(), any(), any());
    }

    @Test
    void mapsRetiredConflictToConflictStatus() throws Exception {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("alice", "department"));
        org.mockito.Mockito.doThrow(
            new ModelSpecException("SUBJECT_DOMAIN_RETIRED_IMMUTABLE", "Retired subject domain cannot be edited", ModelSpecException.Kind.CONFLICT)
        ).when(service).retire(eq("default"), eq("alice"), any(), any());

        mockMvc
            .perform(post("/api/modeling/subject-domains/" + MART + "/retire")
                .header("If-Match", "\"subject-domain:" + MART + ":1:" + "a".repeat(64) + "\""))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SUBJECT_DOMAIN_RETIRED_IMMUTABLE"));
    }

    private static View view(String code, String name, Status status) {
        Instant now = Instant.parse("2026-08-06T12:00:00Z");
        return new View(
            UUID.fromString("30000000-0000-0000-0000-0000000000a1"),
            code,
            name,
            "预算分析主题",
            MART,
            status,
            1,
            "b".repeat(64),
            now,
            now
        );
    }
}
