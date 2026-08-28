package com.yuzhi.dts.ingestion.web.rest;

import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.ingestion.service.IngestionTaskChangeLogService;
import com.yuzhi.dts.ingestion.service.IngestionExecutionQueryService;
import com.yuzhi.dts.ingestion.service.IngestionTaskQueryService;
import com.yuzhi.dts.ingestion.service.IngestionTaskService;
import com.yuzhi.dts.ingestion.service.IngestionAccessContractService;
import com.yuzhi.dts.ingestion.service.IngestionExecutionCommandService;
import com.yuzhi.dts.ingestion.service.IngestionExecutionSubmissionService;
import com.yuzhi.dts.ingestion.service.IngestionTaskDesignService;
import com.yuzhi.dts.ingestion.service.audit.AuditService;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskDesignDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskDesignUpdateRequest;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskRevisionDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionAccessDefaultPolicyDTO;
import com.yuzhi.dts.ingestion.service.etl.AddaxJobService;
import com.yuzhi.dts.ingestion.service.etl.AirflowAdapter;
import com.yuzhi.dts.ingestion.service.etl.ConnectorCapabilityService;
import com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver;
import com.yuzhi.dts.ingestion.service.etl.JdbcMetadataService;
import com.yuzhi.dts.ingestion.service.etl.RealtimeTaskStatusService;
import com.yuzhi.dts.ingestion.service.etl.api.ApiAuthProviderDescriptor;
import com.yuzhi.dts.ingestion.service.etl.api.ApiAuthProviderRegistry;
import com.yuzhi.dts.ingestion.service.openmetadata.OpenMetadataAdapter;
import com.yuzhi.dts.ingestion.config.AirflowProperties;
import com.yuzhi.dts.ingestion.config.ApiProperties;
import com.yuzhi.dts.ingestion.service.infra.PlatformInfraClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.sql.SQLException;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IngestionTaskResource.class)
@AutoConfigureMockMvc(addFilters = false)
class IngestionTaskResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AddaxJobService addaxJobService;

    @MockBean
    private AuditService auditService;

    @MockBean
    private OpenMetadataAdapter openMetadataAdapter;

    @MockBean
    private AirflowAdapter airflowAdapter;

    @MockBean
    private IngestionTaskService ingestionTaskService;

    @MockBean
    private IngestionTaskQueryService ingestionTaskQueryService;

    @MockBean
    private IngestionAccessContractService accessContractService;

    @MockBean
    private IngestionTaskDesignService ingestionTaskDesignService;

    @MockBean
    private IngestionExecutionCommandService ingestionExecutionCommandService;

    @MockBean
    private IngestionExecutionSubmissionService ingestionExecutionSubmissionService;

    @MockBean
    private IngestionExecutionQueryService ingestionExecutionQueryService;

    @MockBean
    private JdbcMetadataService jdbcMetadataService;

    @MockBean
    private IngestionSourceResolver ingestionSourceResolver;

    @MockBean
    private IngestionTaskChangeLogService changeLogService;

    @MockBean
    private ConnectorCapabilityService connectorCapabilityService;

    @MockBean
    private RealtimeTaskStatusService realtimeTaskStatusService;

    @MockBean
    private AirflowProperties airflowProperties;

    @MockBean
    private ApiProperties apiProperties;

    @MockBean
    private ApiAuthProviderRegistry apiAuthProviderRegistry;

    @Test
    void executionListForwardsOptionalRevisionScope() throws Exception {
        when(ingestionTaskService.getExecutions(eq(13L), any(), eq("SUCCESS"), eq(null), eq(4)))
            .thenReturn(org.springframework.data.domain.Page.empty());

        mockMvc.perform(
            get("/api/ingestion/tasks/13/executions")
                .param("status", "SUCCESS")
                .param("revisionNumber", "4")
        ).andExpect(status().isOk());

        verify(ingestionTaskService).getExecutions(eq(13L), any(), eq("SUCCESS"), eq(null), eq(4));
    }

    @Test
    void getTaskDesignReturnsTaskOwnedProjectionAndPlanEtag() throws Exception {
        IngestionTaskDesignDTO design = taskDesign();
        when(ingestionTaskDesignService.getDesign(13L)).thenReturn(design);

        mockMvc.perform(get("/api/ingestion/tasks/13/design"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("ETag", "\"checksum-r4\""))
            .andExpect(jsonPath("$.taskId").value(13))
            .andExpect(jsonPath("$.topology.readonly").value(true));
    }

    @Test
    void updateTaskDesignRequiresMatchingPlanChecksumAndForwardsTypedRequest() throws Exception {
        when(
            ingestionTaskDesignService.saveDesign(
                eq(13L),
                eq("\"checksum-r4\""),
                any(IngestionTaskDesignUpdateRequest.class)
            )
        ).thenReturn(taskDesign());

        mockMvc.perform(
            put("/api/ingestion/tasks/13/design")
                .header("If-Match", "\"checksum-r4\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "taskName":"API 成本中心接入",
                      "sourceDataSourceId":"10000000-0000-0000-0000-000000000013",
                      "sourceType":"httpreader",
                      "sourceConfig":{"endpoint":"/api/cost-centers"},
                      "destinationType":"postgresqlwriter",
                      "destinationConfig":{"table":["ods_api_cost_center"]},
                      "syncMode":"full_refresh",
                      "tableMapping":[{"source":"cost_centers","target":"ods_api_cost_center"}],
                      "syncConfig":{},
                      "postIngestionQualityEnabled":true,
                      "qualityPolicyRef":"dataset:00000000-0000-0000-0000-000000000013"
                    }
                    """)
        ).andExpect(status().isOk());

        verify(ingestionTaskDesignService).saveDesign(
            eq(13L),
            eq("\"checksum-r4\""),
            any(IngestionTaskDesignUpdateRequest.class)
        );
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> auditMeta = ArgumentCaptor.forClass(Map.class);
        verify(auditService).auditAction(
            eq("INGESTION_TASK_UPDATE"),
            eq(AuditStage.SUCCESS),
            eq("API 成本中心接入"),
            auditMeta.capture()
        );
        assertThat(auditMeta.getValue())
            .containsEntry("revisionNumber", 4)
            .containsEntry("planChecksum", "checksum-r4")
            .containsEntry("targetDatasetId", "00000000-0000-0000-0000-000000000013");
    }

    @Test
    void cancelExecutionUsesTaskScopedCommandAndAuditsRun() throws Exception {
        IngestionExecutionDTO execution = new IngestionExecutionDTO();
        execution.setId(90L);
        execution.setTaskId(13L);
        execution.setExecutionId("run-90");
        execution.setRevisionNumber(4);
        execution.setEffectiveConfigChecksum("checksum-r4");
        execution.setTargetDatasetId(UUID.fromString("00000000-0000-0000-0000-000000000013"));
        execution.setStatus("cancelled");
        when(ingestionExecutionCommandService.cancel(13L, 90L)).thenReturn(execution);

        mockMvc.perform(post("/api/ingestion/tasks/13/executions/90/cancel"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("cancelled"));

        verify(ingestionExecutionCommandService).cancel(13L, 90L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> auditMeta = ArgumentCaptor.forClass(Map.class);
        verify(auditService).auditAction(
            eq("INGESTION_TASK_EXECUTE"),
            eq(AuditStage.SUCCESS),
            eq("13"),
            auditMeta.capture()
        );
        assertThat(auditMeta.getValue())
            .containsEntry("executionId", 90L)
            .containsEntry("executionRunId", "run-90")
            .containsEntry("revisionNumber", 4)
            .containsEntry("planChecksum", "checksum-r4")
            .containsEntry("targetDatasetId", "00000000-0000-0000-0000-000000000013");
    }

    private IngestionTaskDesignDTO taskDesign() {
        return new IngestionTaskDesignDTO(
            13L,
            "API 成本中心接入",
            null,
            4,
            "ACTIVE",
            "active",
            new IngestionTaskDesignDTO.SourceDesign(
                UUID.fromString("10000000-0000-0000-0000-000000000013"),
                "httpreader",
                new ObjectMapper().createObjectNode()
            ),
            new IngestionTaskDesignDTO.DestinationDesign(
                "postgresqlwriter",
                new ObjectMapper().createObjectNode(),
                new IngestionTaskDesignDTO.DestinationAssetRef(
                    UUID.fromString("00000000-0000-0000-0000-000000000013"),
                    "dataset:00000000-0000-0000-0000-000000000013",
                    "POLICY_REF"
                )
            ),
            "full_refresh",
            null,
            new ObjectMapper().createArrayNode(),
            new ObjectMapper().createObjectNode(),
            new IngestionTaskDesignDTO.PostIngestionQuality(
                true,
                "dataset:00000000-0000-0000-0000-000000000013"
            ),
            "checksum-r4",
            new IngestionTaskDesignDTO.ValidationResult(true, List.of()),
            new IngestionTaskDesignDTO.TopologyProjection(true, "checksum-r4", List.of(), List.of()),
            new IngestionTaskDesignDTO.LegacyDsl(false, false, null)
        );
    }

    @Test
    void listTasksShouldForwardServerSideAccessFilters() throws Exception {
        when(
            ingestionTaskQueryService.findAll(
                eq("active"),
                eq("api"),
                eq("crm"),
                eq("healthy"),
                eq(UUID.fromString("00000000-0000-0000-0000-000000000091")),
                any(org.springframework.data.domain.Pageable.class)
            )
        ).thenReturn(org.springframework.data.domain.Page.<IngestionTaskDTO>empty());

        mockMvc.perform(
            get("/api/ingestion/tasks/list")
                .param("status", "active")
                .param("sourceKind", "api")
                .param("query", "crm")
                .param("health", "healthy")
                .param("sourceDataSourceId", "00000000-0000-0000-0000-000000000091")
        ).andExpect(status().isOk());

        verify(ingestionTaskQueryService).findAll(
            eq("active"),
            eq("api"),
            eq("crm"),
            eq("healthy"),
            eq(UUID.fromString("00000000-0000-0000-0000-000000000091")),
            any(org.springframework.data.domain.Pageable.class)
        );
    }

    @Test
    void getTaskShouldExposeActiveLifecycleSeparatelyFromDraftRevisionOverlay() throws Exception {
        IngestionTaskDTO detail = new IngestionTaskDTO();
        detail.setId(48L);
        detail.setName("draft-r13-name");
        detail.setStatus("active");
        detail.setRevisionState("DRAFT");
        detail.setRevisionNumber(13);
        detail.setSourceConfig(new ObjectMapper().readTree("""
            {"endpoint":"https://api.example.test","auth":{"clientSecret":"must-not-leak","token":"must-not-leak"}}
            """));
        when(ingestionTaskQueryService.findOne(48L)).thenReturn(Optional.of(detail));

        mockMvc.perform(get("/api/ingestion/tasks/48"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("active"))
            .andExpect(jsonPath("$.revisionState").value("DRAFT"))
            .andExpect(jsonPath("$.revisionNumber").value(13))
            .andExpect(jsonPath("$.sourceConfig.endpoint").value("https://api.example.test"))
            .andExpect(jsonPath("$.sourceConfig.auth.clientSecret").doesNotExist())
            .andExpect(jsonPath("$.sourceConfig.auth.token").doesNotExist());
    }

    @Test
    void getTaskRevisionsShouldExposeOneActiveAndOptionalDraftRevision() throws Exception {
        when(accessContractService.getTaskRevisions(48L)).thenReturn(List.of(
            new IngestionTaskRevisionDTO(
                12,
                "ACTIVE",
                "database",
                "checksum-r12",
                3,
                "policy-r3",
                null,
                "operator",
                Instant.parse("2026-07-31T00:00:00Z"),
                "operator",
                Instant.parse("2026-07-31T00:01:00Z"),
                "ACTIVE",
                null,
                Instant.parse("2026-07-31T00:02:00Z")
            ),
            new IngestionTaskRevisionDTO(
                13,
                "DRAFT",
                "database",
                "checksum-r13",
                3,
                "policy-r3",
                null,
                "operator",
                Instant.parse("2026-07-31T01:00:00Z"),
                null,
                null,
                "STAGED",
                null,
                Instant.parse("2026-07-31T01:01:00Z")
            )
        ));

        mockMvc.perform(get("/api/ingestion/tasks/48/revisions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].revisionNumber").value(12))
            .andExpect(jsonPath("$[0].revisionState").value("ACTIVE"))
            .andExpect(jsonPath("$[0].dagDeploymentStatus").value("ACTIVE"))
            .andExpect(jsonPath("$[1].revisionNumber").value(13))
            .andExpect(jsonPath("$[1].revisionState").value("DRAFT"))
            .andExpect(jsonPath("$[1].dagDeploymentStatus").value("STAGED"));
    }

    @Test
    void getAccessDefaultPolicyShouldExposeVersionedReadOnlyContract() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        when(accessContractService.getActiveDefaultPolicy()).thenReturn(
            new IngestionAccessDefaultPolicyDTO(
                "GLOBAL",
                3,
                "ACTIVE",
                mapper.createObjectNode().put("scheduleType", "manual"),
                "checksum-v3",
                Instant.parse("2026-07-31T00:00:00Z")
            )
        );

        mockMvc.perform(get("/api/ingestion/access/default-policy"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.policyKey").value("GLOBAL"))
            .andExpect(jsonPath("$.version").value(3))
            .andExpect(jsonPath("$.checksum").value("checksum-v3"));
    }

    @Test
    void createTask_apiDraftNormalizesRawOdsLandingAndTableMapping() throws Exception {
        UUID sourceId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PlatformInfraClient.DataSourceDetail detail = new PlatformInfraClient.DataSourceDetail(
            sourceId,
            "CRM API",
            "api",
            null,
            null,
            null,
            null,
            Map.of("readerType", "httpreader", "connectorType", "api"),
            Map.of(),
            "ACTIVE"
        );
        when(ingestionSourceResolver.resolve(eq(sourceId), anyList()))
            .thenReturn(new IngestionSourceResolver.ResolvedSource(
                "httpreader",
                Map.of(
                    "readerType", "httpreader",
                    "secrets", Map.of("token", "managed-api-token"),
                    "auth", Map.of("clientSecret", "managed-client-secret")
                ),
                detail
            ));
        IngestionTaskDTO created = new IngestionTaskDTO();
        created.setId(99L);
        created.setName("crm-orders");
        when(ingestionTaskService.create(any(IngestionTaskDTO.class), any(), eq(true))).thenReturn(created);

        mockMvc.perform(post("/api/ingestion/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "draft": true,
                      "runNow": true,
                      "name": "crm-orders",
                      "source": {
                        "dataSourceId": "11111111-2222-3333-4444-555555555555",
                        "type": "api",
                        "config": {
                          "sourceSystem": "CRM",
                          "resource": {
                            "path": "/v1/orders",
                            "fields": [{"sourceField": "id", "targetColumn": "id"}]
                          }
                        }
                      },
	                      "sync": {"mode": "full_refresh"},
	                      "streams": {"selection": "manual", "include": ["orders"]},
	                      "destination": {
	                        "usePlatformDefault": true,
	                        "definitionId": "postgresqlwriter",
	                        "config": {
	                          "targetDataSourceId": "22222222-3333-4444-5555-666666666666",
	                          "jdbcUrl": "jdbc:postgresql://pg:5432/biadmin",
	                          "username": "biadmin"
	                        }
	                      },
	                      "airflow": {"enabled": false}
	                    }
	                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.task.id").value(99));

        ArgumentCaptor<IngestionTaskDTO> captor = ArgumentCaptor.forClass(IngestionTaskDTO.class);
        verify(ingestionTaskService).create(captor.capture(), any(), eq(true));
        IngestionTaskDTO task = captor.getValue();
        assertThat(task.getSourceType()).isEqualTo("httpreader");
        assertThat(task.getStatus()).isEqualTo("draft");
        assertThat(task.getAirflowEnabled()).isFalse();
        assertThat(task.getSourceConfig().get("resource").has("fields")).isFalse();
        assertThat(task.getSourceConfig().has("secrets")).isFalse();
        assertThat(task.getSourceConfig().path("auth").has("clientSecret")).isFalse();
        assertThat(task.getSourceConfig().toString()).doesNotContain("managed-api-token", "managed-client-secret");
        assertThat(task.getSourceConfig().get("resource").get("targetTable").asText()).isEqualTo("ods_api_crm_v1_orders");
	        assertThat(task.getSourceConfig().get("resource").get("landing").get("rawRecordColumn").asText()).isEqualTo("_dts_raw_record");
	        assertThat(task.getDestinationType()).isEqualTo("postgresqlwriter");
	        assertThat(task.getDestinationConfig().get("targetDataSourceId").asText()).isEqualTo("22222222-3333-4444-5555-666666666666");
        assertThat(task.getTableMapping()).hasSize(1);
        assertThat(task.getTableMapping().get(0).get("source").asText()).isEqualTo("v1_orders");
        assertThat(task.getTableMapping().get(0).get("target").asText()).isEqualTo("ods_api_crm_v1_orders");
        verify(ingestionTaskService, never()).executeAsync(99L);
    }

    @Test
    void createTask_jdbcFlagsCannotBypassDraftAdmission() throws Exception {
        UUID sourceId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        Map<String, Object> readerConfig = new java.util.LinkedHashMap<>();
        readerConfig.put("readerType", "mysqlreader");
        readerConfig.put("table", List.of("orders"));
        IngestionSourceResolver.ResolvedSource resolvedSource =
            new IngestionSourceResolver.ResolvedSource("mysqlreader", readerConfig, null);
        when(ingestionSourceResolver.resolve(eq(sourceId), anyList())).thenReturn(resolvedSource);
        when(connectorCapabilityService.normalizeSyncMode(any())).thenReturn("full_refresh");
        IngestionTaskDTO created = new IngestionTaskDTO();
        created.setId(100L);
        created.setName("jdbc-orders");
        when(ingestionTaskService.create(any(IngestionTaskDTO.class), eq(resolvedSource), eq(true))).thenReturn(created);

        mockMvc.perform(post("/api/ingestion/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "draft": false,
                      "runNow": true,
                      "name": "jdbc-orders",
                      "source": {
                        "dataSourceId": "11111111-2222-3333-4444-555555555555",
                        "type": "mysql",
                        "config": {}
                      },
                      "destination": {
                        "usePlatformDefault": true,
                        "definitionId": "postgresqlwriter",
                        "config": {
                          "jdbcUrl": "jdbc:postgresql://pg:5432/biadmin",
                          "username": "biadmin"
                        }
                      },
                      "sync": {"mode": "full_refresh"},
                      "streams": {"selection": "manual", "include": ["orders"]},
                      "airflow": {"enabled": false}
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.task.id").value(100));

        ArgumentCaptor<IngestionTaskDTO> captor = ArgumentCaptor.forClass(IngestionTaskDTO.class);
        verify(ingestionTaskService).create(captor.capture(), eq(resolvedSource), eq(true));
        assertThat(captor.getValue().getStatus()).isEqualTo("draft");
        verify(ingestionTaskService, never()).executeAsync(100L);
        verify(addaxJobService, never()).createJob(any(), any(), any(), any(), any(), any());
    }

    @Test
    void createTask_fileFlagsCannotBypassDraftAdmission() throws Exception {
        when(connectorCapabilityService.normalizeSyncMode(any())).thenReturn("full_refresh");
        IngestionTaskDTO created = new IngestionTaskDTO();
        created.setId(101L);
        created.setName("file-orders");
        when(ingestionTaskService.create(any(IngestionTaskDTO.class), org.mockito.ArgumentMatchers.isNull(), eq(true)))
            .thenReturn(created);

        mockMvc.perform(post("/api/ingestion/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "draft": false,
                      "runNow": true,
                      "name": "file-orders",
                      "source": {
                        "type": "excel",
                        "config": {
                          "_fileId": "upload-101",
                          "_fileName": "orders.xlsx"
                        }
                      },
                      "sync": {"mode": "full_refresh"},
                      "airflow": {"enabled": false}
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.task.id").value(101));

        ArgumentCaptor<IngestionTaskDTO> captor = ArgumentCaptor.forClass(IngestionTaskDTO.class);
        verify(ingestionTaskService).create(captor.capture(), org.mockito.ArgumentMatchers.isNull(), eq(true));
        assertThat(captor.getValue().getStatus()).isEqualTo("draft");
        verify(ingestionTaskService, never()).executeAsync(101L);
        verify(addaxJobService, never()).createJob(any(), any(), any(), any(), any(), any());
    }

    @Test
    void admitTask_returnsDirectActiveTaskDto() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode seal = objectMapper.readTree("""
            {
              "sealId": "seal-001",
              "subjectType": "FILE",
              "subjectKey": "file:upload-001",
              "effectiveLevel": "INTERNAL",
              "snapshotVersion": 1,
              "checksum": "0123456789abcdef0123456789abcdef",
              "sealedAt": "2026-07-28T00:00:00Z",
              "fileFloor": "INTERNAL"
            }
            """);
        JsonNode fields = objectMapper.createObjectNode().put("customer_id", "INTERNAL");
        IngestionTaskDTO admitted = new IngestionTaskDTO();
        admitted.setId(1L);
        admitted.setName("file-orders");
        admitted.setStatus("active");
        admitted.setRevisionNumber(4);
        admitted.setEffectiveConfigChecksum("checksum-r4");
        admitted.setTargetDatasetId(UUID.fromString("00000000-0000-0000-0000-000000000013"));
        admitted.setClassificationSeal(seal);
        admitted.setFieldClassifications(fields);
        when(ingestionTaskService.admit(eq(1L), any(JsonNode.class), any(JsonNode.class))).thenReturn(admitted);

        mockMvc.perform(post("/api/ingestion/tasks/1/admit")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "classificationSeal": {
                        "sealId": "seal-001",
                        "subjectType": "FILE",
                        "subjectKey": "file:upload-001",
                        "effectiveLevel": "INTERNAL",
                        "snapshotVersion": 1,
                        "checksum": "0123456789abcdef0123456789abcdef",
                        "sealedAt": "2026-07-28T00:00:00Z",
                        "fileFloor": "INTERNAL"
                      },
                      "fieldClassifications": {
                        "customer_id": "INTERNAL"
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.status").value("active"))
            .andExpect(jsonPath("$.classificationSeal.sealId").value("seal-001"))
            .andExpect(jsonPath("$.fieldClassifications.customer_id").value("INTERNAL"));

        verify(ingestionTaskService).admit(1L, seal, fields);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> auditMeta = ArgumentCaptor.forClass(Map.class);
        verify(auditService).auditAction(
            eq("INGESTION_TASK_UPDATE"),
            eq(AuditStage.SUCCESS),
            eq("file-orders"),
            auditMeta.capture()
        );
        assertThat(auditMeta.getValue())
            .containsEntry("summary", "完成密级封存与生产准入")
            .containsEntry("taskId", 1L)
            .containsEntry("revisionNumber", 4)
            .containsEntry("planChecksum", "checksum-r4")
            .containsEntry("targetDatasetId", "00000000-0000-0000-0000-000000000013");
    }

    @Test
    void admitTask_returnsConflictWhenSealValidationFails() throws Exception {
        when(ingestionTaskService.admit(eq(1L), any(), any()))
            .thenThrow(new IllegalStateException("CLASSIFICATION_SEAL_INVALID"));

        mockMvc.perform(post("/api/ingestion/tasks/1/admit")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "classificationSeal": {"sealId": "invalid"},
                      "fieldClassifications": {}
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.message").value("请求状态冲突"))
            .andExpect(jsonPath("$.code").value("HTTP_409"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> auditMeta = ArgumentCaptor.forClass(Map.class);
        verify(auditService).auditAction(
            eq("INGESTION_TASK_UPDATE"),
            eq(AuditStage.FAIL),
            eq("1"),
            auditMeta.capture()
        );
        assertThat(auditMeta.getValue())
            .containsEntry("summary", "密级封存与生产准入失败")
            .containsEntry("taskId", 1L)
            .containsEntry("error", "CLASSIFICATION_SEAL_INVALID");
    }

    @Test
    void updateTask_returnsConflictForDraftToActiveBypass() throws Exception {
        when(connectorCapabilityService.normalizeSyncMode(any())).thenReturn("full_refresh");
        when(ingestionTaskService.update(eq(1L), any(IngestionTaskDTO.class)))
            .thenThrow(new IllegalStateException("Task must enter active status through /admit"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/ingestion/tasks/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "id": 1,
                      "name": "draft-task",
                      "sourceType": "mysqlreader",
                      "sourceDataSourceId": "11111111-2222-3333-4444-555555555555",
                      "syncMode": "full_refresh",
                      "status": "active"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.message").value("请求状态冲突"))
            .andExpect(jsonPath("$.code").value("HTTP_409"));
    }

    @Test
    void updateTask_keepsAllTablePlaceholderDynamicWhenAddingPrefix() throws Exception {
        UUID sourceId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        Map<String, Object> readerConfig = new java.util.LinkedHashMap<>();
        readerConfig.put("readerType", "mysqlreader");
        readerConfig.put("table", List.of("${table}"));
        IngestionSourceResolver.ResolvedSource resolvedSource =
            new IngestionSourceResolver.ResolvedSource("mysqlreader", readerConfig, null);
        when(ingestionSourceResolver.resolve(eq(sourceId), anyList())).thenReturn(resolvedSource);
        when(connectorCapabilityService.normalizeSyncMode(any())).thenReturn("full_refresh");
        when(ingestionTaskService.update(eq(3L), any(IngestionTaskDTO.class)))
            .thenAnswer(invocation -> invocation.getArgument(1));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/ingestion/tasks/3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "id": 3,
                      "name": "dtstest1",
                      "sourceType": "mysqlreader",
                      "sourceDataSourceId": "11111111-2222-3333-4444-555555555555",
                      "sourceConfig": {"readerType": "mysqlreader", "table": ["${table}"]},
                      "destinationType": "postgresqlwriter",
                      "destinationConfig": {
                        "targetDataSourceId": "22222222-3333-4444-5555-666666666666"
                      },
                      "syncMode": "full_refresh",
                      "syncPrefix": "ods_",
                      "status": "draft",
                      "tableMapping": [{"source": "${table}", "target": "ods_${table}"}]
                    }
                    """))
            .andExpect(status().isOk());

        ArgumentCaptor<IngestionTaskDTO> captor = ArgumentCaptor.forClass(IngestionTaskDTO.class);
        verify(ingestionTaskService).update(eq(3L), captor.capture());
        IngestionTaskDTO updated = captor.getValue();
        assertThat(updated.getTableMapping().isArray()).isTrue();
        assertThat(updated.getTableMapping()).isEmpty();
        assertThat(updated.getDestinationConfig().get("tablePrefix").asText()).isEqualTo("ods_");
        assertThat(updated.getDestinationConfig().get("table").isArray()).isTrue();
        assertThat(updated.getDestinationConfig().get("table").get(0).asText()).isEqualTo("${table}");
        assertThat(updated.getDestinationConfig().toString()).doesNotContain("ods_ods_", "$ods_");
    }

    @Test
    void updateTask_rejectsRetiredDirectDbtSelectors() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/ingestion/tasks/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "id": 1,
                      "name": "legacy-dbt-task",
                      "sourceType": "mysqlreader",
                      "sourceDataSourceId": "11111111-2222-3333-4444-555555555555",
                      "syncMode": "full_refresh",
                      "dbtDagSelector": "legacy-project"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("请求体解析失败"))
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

        verify(ingestionTaskService, never()).update(eq(1L), any(IngestionTaskDTO.class));
    }

    @Test
    void createTask_apiDraftRejectsDisabledAuthProvider() throws Exception {
        UUID sourceId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PlatformInfraClient.DataSourceDetail detail = new PlatformInfraClient.DataSourceDetail(
            sourceId,
            "CRM API",
            "api",
            null,
            null,
            null,
            null,
            Map.of("readerType", "httpreader", "connectorType", "api"),
            Map.of(),
            "ACTIVE"
        );
        when(ingestionSourceResolver.resolve(eq(sourceId), anyList()))
            .thenReturn(new IngestionSourceResolver.ResolvedSource("httpreader", Map.of("readerType", "httpreader"), detail));
        when(apiAuthProviderRegistry.findDescriptor("mtls"))
            .thenReturn(Optional.of(new ApiAuthProviderDescriptor("mtls", "mTLS", "preview", List.of(), true, false)));

        mockMvc.perform(post("/api/ingestion/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "draft": true,
                      "name": "crm-orders",
                      "source": {
                        "dataSourceId": "11111111-2222-3333-4444-555555555555",
                        "type": "api",
                        "config": {
                          "sourceSystem": "CRM",
                          "auth": {"provider": "mtls"},
                          "resource": {"path": "/v1/orders"}
                        }
                      },
                      "sync": {"mode": "full_refresh"},
                      "streams": {"selection": "manual", "include": ["orders"]},
                      "airflow": {"enabled": false}
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("请求参数错误"))
            .andExpect(jsonPath("$.code").value("HTTP_400"));

        verify(ingestionTaskService, never()).create(any(IngestionTaskDTO.class), any(), eq(true));
    }

    @Test
    void discoverTables_requiresSourceConfig() throws Exception {
        mockMvc.perform(post("/api/ingestion/metadata/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("请求参数错误"))
            .andExpect(jsonPath("$.code").value("HTTP_400"));
    }

    @Test
    void discoverTables_returnsSafeActionableAuthenticationFailure() throws Exception {
        UUID sourceId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        JdbcMetadataService.JdbcConnectionInfo connectionInfo = new JdbcMetadataService.JdbcConnectionInfo(
            "jdbc:mysql://database.example:3306/app",
            "sensitive-user",
            "sensitive-password",
            null,
            null,
            Map.of()
        );
        when(ingestionSourceResolver.resolve(eq(sourceId), anyList()))
            .thenReturn(new IngestionSourceResolver.ResolvedSource("mysqlreader", Map.of(), null));
        when(ingestionSourceResolver.resolveJdbcInfo(sourceId)).thenReturn(connectionInfo);
        when(jdbcMetadataService.listTables(eq(connectionInfo), eq(null), eq(null), eq(0)))
            .thenThrow(
                JdbcMetadataService.MetadataDiscoveryException.authenticationFailure(
                    new SQLException("Access denied for sensitive-user; password=sensitive-password", "28000", 1045)
                )
            );

        String response = mockMvc.perform(post("/api/ingestion/metadata/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "source": {"dataSourceId": "11111111-2222-3333-4444-555555555555"},
                      "filter": {"limit": 0, "includeColumns": false}
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(502))
            .andExpect(jsonPath("$.message").value("数据库认证失败，请检查用户名、密码及来源 IP 授权"))
            .andExpect(jsonPath("$.code").value("JDBC_METADATA_AUTH_FAILED"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertThat(response).doesNotContain("sensitive-user", "sensitive-password", "Access denied");
    }

    @Test
    void deleteTask_returnsApiResponse() throws Exception {
        IngestionTaskDTO task = new IngestionTaskDTO();
        task.setId(1L);
        task.setName("demo-task");
        when(ingestionTaskService.delete(1L)).thenReturn(task);
        when(ingestionTaskService.cleanupRetiredTaskArtifacts(1L)).thenReturn(
            new IngestionTaskService.RuntimeArtifactCleanupResult(true, true, false, null, null)
        );

        mockMvc.perform(delete("/api/ingestion/tasks/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.taskId").value(1))
            .andExpect(jsonPath("$.data.runtimeArtifactCleanup.retryable").value(false));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(ingestionTaskService);
        order.verify(ingestionTaskService).delete(1L);
        order.verify(ingestionTaskService).cleanupRetiredTaskArtifacts(1L);
    }

    @Test
    void deleteTask_returnsConflictWhenExecutionIsInProgress() throws Exception {
        when(ingestionTaskService.delete(1L)).thenThrow(new IllegalStateException("任务正在执行，无法删除"));

        mockMvc.perform(delete("/api/ingestion/tasks/1"))
            .andExpect(status().isConflict());

        verify(ingestionTaskService, never()).cleanupRetiredTaskArtifacts(1L);

        verify(auditService).auditAction(
            eq("INGESTION_TASK_DELETE"),
            eq(AuditStage.FAIL),
            eq("1"),
            argThat(metadata -> "IllegalStateException".equals(metadata.get("errorType")))
        );
    }

    @Test
    void rebuildApiDags_returnsMigrationSummary() throws Exception {
        when(ingestionTaskService.rebuildApiDags()).thenReturn(
            Map.of("total", 3, "migrated", 1, "skipped", 2, "failed", 0)
        );

        mockMvc.perform(post("/api/ingestion/tasks/dags/rebuild-api"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.migrated").value(1))
            .andExpect(jsonPath("$.skipped").value(2))
            .andExpect(jsonPath("$.failed").value(0));

        verify(ingestionTaskService).rebuildApiDags();
    }

    @Test
    void executeTaskAsync_returnsConflictWhenValidationFails() throws Exception {
        when(ingestionTaskService.validateAsyncExecutionRequest(1L))
            .thenThrow(new IllegalStateException("任务仍在运行中，请稍后重试"));

        mockMvc.perform(post("/api/ingestion/tasks/1/execute/async"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.message").value("请求状态冲突"))
            .andExpect(jsonPath("$.code").value("HTTP_409"));

        verify(ingestionTaskService, never()).executeAsync(1L);
    }

    @Test
    void executeTaskAsyncWithIdempotencyKeyReturnsThePersistedLedgerIdentity() throws Exception {
        IngestionTaskDTO task = new IngestionTaskDTO();
        task.setId(1L);
        task.setName("demo-task");
        IngestionExecutionDTO execution = new IngestionExecutionDTO();
        execution.setId(91L);
        execution.setExecutionId("preparing-91");
        execution.setRevisionNumber(4);
        execution.setEffectiveConfigChecksum("checksum-r4");
        execution.setTargetDatasetId(UUID.fromString("00000000-0000-0000-0000-000000000013"));
        when(ingestionTaskService.validateAsyncExecutionRequest(1L)).thenReturn(task);
        when(ingestionExecutionSubmissionService.submitCommand(1L, "browser-command-1"))
            .thenReturn(new com.yuzhi.dts.ingestion.service.IngestionExecutionSubmissionService.SubmissionResult(execution, false));

        mockMvc.perform(
            post("/api/ingestion/tasks/1/execute/async")
                .header("Idempotency-Key", "browser-command-1")
        )
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.executionId").value(91))
            .andExpect(jsonPath("$.revisionNumber").value(4))
            .andExpect(jsonPath("$.planChecksum").value("checksum-r4"))
            .andExpect(jsonPath("$.idempotencyProtected").value(true))
            .andExpect(jsonPath("$.idempotent").value(false));

        verify(ingestionExecutionSubmissionService).submitCommand(1L, "browser-command-1");
        verify(ingestionTaskService, never()).executeAsync(1L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> auditMeta = ArgumentCaptor.forClass(Map.class);
        verify(auditService).auditAction(
            eq("INGESTION_TASK_EXECUTE"),
            eq(AuditStage.SUCCESS),
            eq("1"),
            auditMeta.capture()
        );
        assertThat(auditMeta.getValue())
            .containsEntry("executionId", 91L)
            .containsEntry("revisionNumber", 4)
            .containsEntry("planChecksum", "checksum-r4")
            .containsEntry("targetDatasetId", "00000000-0000-0000-0000-000000000013");
    }

    @Test
    void retryExecutionAsync_returnsNotFoundWhenExecutionIsMissing() throws Exception {
        doThrow(new IllegalArgumentException("Execution not found: 9"))
            .when(ingestionTaskService)
            .validateAsyncRetryRequest(1L, 9L, "FAILED_ONLY");

        mockMvc.perform(post("/api/ingestion/tasks/1/executions/9/retry/async"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("资源不存在"))
            .andExpect(jsonPath("$.code").value("HTTP_404"));

        verify(ingestionTaskService, never()).retryExecutionAsync(1L, 9L, "FAILED_ONLY");
    }

    @Test
    void retryExecutionAsync_doesNotQueueDraftTask() throws Exception {
        doThrow(new IllegalStateException("Task is not in executable status: draft"))
            .when(ingestionTaskService)
            .validateAsyncRetryRequest(1L, 9L, "FAILED_ONLY");

        mockMvc.perform(post("/api/ingestion/tasks/1/executions/9/retry/async"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.message").value("请求状态冲突"))
            .andExpect(jsonPath("$.code").value("HTTP_409"));

        verify(ingestionTaskService, never()).retryExecutionAsync(1L, 9L, "FAILED_ONLY");
    }

    @Test
    void retryExecutionAsync_doesNotQueueTaskWithInvalidSeal() throws Exception {
        doThrow(new IllegalStateException("CLASSIFICATION_SEAL_REQUIRED"))
            .when(ingestionTaskService)
            .validateAsyncRetryRequest(1L, 9L, "FAILED_ONLY");

        mockMvc.perform(post("/api/ingestion/tasks/1/executions/9/retry/async"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.message").value("请求状态冲突"))
            .andExpect(jsonPath("$.code").value("HTTP_409"));

        verify(ingestionTaskService, never()).retryExecutionAsync(1L, 9L, "FAILED_ONLY");
    }

    @Test
    void retryExecutionAsync_doesNotQueueSuccessfulExecutionForFailedOnly() throws Exception {
        doThrow(new IllegalStateException("仅失败执行可使用 FAILED_ONLY 重试模式"))
            .when(ingestionTaskService)
            .validateAsyncRetryRequest(1L, 9L, "FAILED_ONLY");

        mockMvc.perform(post("/api/ingestion/tasks/1/executions/9/retry/async"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.message").value("请求状态冲突"))
            .andExpect(jsonPath("$.code").value("HTTP_409"));

        verify(ingestionTaskService, never()).retryExecutionAsync(1L, 9L, "FAILED_ONLY");
        verify(auditService, never()).auditAction(
            eq("INGESTION_EXECUTION_RETRY"),
            eq(AuditStage.SUCCESS),
            any(),
            any()
        );
    }

    @Test
    void executeTaskAsync_returnsServiceUnavailableWhenExecutorRejectsSubmission() throws Exception {
        IngestionTaskDTO task = new IngestionTaskDTO();
        task.setId(1L);
        task.setName("demo-task");
        when(ingestionTaskService.validateAsyncExecutionRequest(1L)).thenReturn(task);
        when(ingestionTaskService.executeAsync(1L))
            .thenThrow(new TaskRejectedException("queue is full"));

        mockMvc.perform(post("/api/ingestion/tasks/1/execute/async"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.message").value("服务内部错误"))
            .andExpect(jsonPath("$.code").value("HTTP_503"));
    }

    @Test
    void retryExecutionAsync_returnsServiceUnavailableWhenExecutorRejectsSubmission() throws Exception {
        when(ingestionTaskService.retryExecutionAsync(1L, 9L, "FAILED_ONLY"))
            .thenThrow(new TaskRejectedException("queue is full"));

        mockMvc.perform(post("/api/ingestion/tasks/1/executions/9/retry/async"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.message").value("服务内部错误"))
            .andExpect(jsonPath("$.code").value("HTTP_503"));
    }
}
