package com.yuzhi.dts.platform.web.rest;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.security.session.PortalSessionInactivityFilter;
import com.yuzhi.dts.platform.service.modeling.ModelingVNextApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelingVNextContract;
import com.yuzhi.dts.platform.service.modeling.PjmModelingFixture;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.web.filter.AuditLoggingFilter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    value = ModelingVNextResource.class,
    excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class,
    properties = { "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration" }
)
@AutoConfigureMockMvc(addFilters = false)
class ModelingVNextResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ModelingVNextApplicationService service;

    @MockBean
    private AuditService auditService;

    @MockBean
    private PortalSessionInactivityFilter portalSessionInactivityFilter;

    @MockBean
    private AuditLoggingFilter auditLoggingFilter;

    @Test
    void listsBusinessObjectsUsingTenantAndProcessFilters() throws Exception {
        ModelingVNextContract.BusinessObject object = PjmModelingFixture.projectNode().businessObject();
        when(service.listBusinessObjects(eq("tenant-pjm"), eq("project-node-plan-loop"), eq("DRAFT"))).thenReturn(List.of(object));

        mockMvc
            .perform(get("/api/modeling/vnext/business-objects")
                .header("X-Tenant-Id", "tenant-pjm")
                .param("processId", "project-node-plan-loop")
                .param("status", "DRAFT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].code").value("project_node"));
    }

    @Test
    void createsBusinessObjectAndCarriesRevisionAndIdempotency() throws Exception {
        ModelingVNextContract.BusinessObject object = PjmModelingFixture.projectNode().businessObject();
        when(service.saveBusinessObject(eq("tenant-pjm"), eq(object), eq(3), eq("idem-pjm-3"))).thenReturn(object);

        mockMvc
            .perform(post("/api/modeling/vnext/business-objects")
                .header("X-Tenant-Id", "tenant-pjm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "id":"pjm-project-node","code":"project_node","name":"项目节点",
                      "description":"项目节点计划闭环中的明细事实对象。","objectKind":"FACT",
                      "processId":"project-node-plan-loop","businessKey":["project_no","subsystem","node_task","plan_date"],
                      "grain":{"statement":"一行代表一个项目在一个计划日期上的节点任务","keys":["project_no","subsystem","node_task","plan_date"]},
                      "sourceRefs":[{"kind":"TABLE","ref":"ods_project_subject_domain_v2","layer":"ODS"}],
                      "status":"DRAFT","implementationMode":"DESIGNER_GENERATED",
                      "revision":3,"idempotencyKey":"idem-pjm-3"
                    }
                    """
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value("pjm-project-node"));

        verify(service).saveBusinessObject(eq("tenant-pjm"), eq(object), eq(3), eq("idem-pjm-3"));
    }

    @Test
    void callbackWritesAnAuditEventWithExternalRunIdentifiers() throws Exception {
        ModelingVNextApplicationService.RunView run = new ModelingVNextApplicationService.RunView(
            "run-1", "model-1", 1, "RUNNING", "batch-1", "addax-1", "dag-pjm", "airflow-1", "dbt-1", "model.pjm", "dwd_project_node", "ok", null
        );
        when(service.callbackRun(eq("tenant-pjm"), eq("run-1"), org.mockito.ArgumentMatchers.any())).thenReturn(run);

        mockMvc
            .perform(post("/api/modeling/vnext/runs/run-1/callback")
                .header("X-Tenant-Id", "tenant-pjm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"state\":\"RUNNING\",\"addaxTaskId\":\"addax-1\",\"airflowRunId\":\"airflow-1\",\"dbtRunId\":\"dbt-1\",\"message\":\"ok\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.state").value("RUNNING"));

        verify(auditService).auditAction(eq("MODELING_VNEXT_RUN_CALLBACK"), eq(com.yuzhi.dts.common.audit.AuditStage.SUCCESS), eq("run-1"), org.mockito.ArgumentMatchers.argThat(payload -> payload.toString().contains("airflow-1")));
    }

    @Test
    void exposesReleaseGateBlockersForModelLedger() throws Exception {
        when(service.releaseGate(eq("tenant-pjm"), eq("model-1"))).thenReturn(
            new ModelingVNextApplicationService.ReleaseGateView("model-1", false, "BLOCKED", List.of("ARTIFACT_CHECKSUM_DRIFT"))
        );

        mockMvc
            .perform(get("/api/modeling/vnext/model-specs/model-1/release-gate").header("X-Tenant-Id", "tenant-pjm"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.publishable").value(false))
            .andExpect(jsonPath("$.data.blockers[0]").value("ARTIFACT_CHECKSUM_DRIFT"));
    }
}
