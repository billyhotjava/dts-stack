package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.security.ClassificationUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationService;
import com.yuzhi.dts.platform.service.infra.DefaultDestinationSyncService;
import com.yuzhi.dts.platform.service.infra.DefaultDestinationSyncService.DefaultDestinationSnapshot;
import com.yuzhi.dts.platform.service.etl.OdsTableMappingSyncService;
import com.yuzhi.dts.platform.service.ingestion.IngestionClassificationAdmissionService;
import com.yuzhi.dts.platform.service.ingestion.IngestionAccessDecisionService;
import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.service.ops.ExternalRunLogService;
import com.yuzhi.dts.platform.security.session.PortalSessionInactivityFilter;
import com.yuzhi.dts.platform.web.filter.AuditLoggingFilter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

@Import(IngestionClassificationAdmissionService.class)
@WebMvcTest(
    value = IngestionTaskProxyResource.class,
    excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration"
    }
)
@AutoConfigureMockMvc(addFilters = false)
class IngestionTaskProxyResourceTest {

    private static final String PREVIOUS_SOURCE_KEY = "data-source:aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IngestionServiceClient ingestionClient;

    @MockBean
    private DefaultDestinationSyncService destinationSyncService;

    @MockBean
    private AuditService auditService;

    @MockBean
    private OdsTableMappingSyncService odsTableMappingSyncService;

    @MockBean
    private ExternalRunLogService externalRunLogService;

    @MockBean
    private InfraDataSourceRepository dataSourceRepository;

    @MockBean
    private CatalogClassificationService classificationService;

    @MockBean
    private ClassificationUtils classificationUtils;

    @MockBean
    private IngestionAccessDecisionService accessDecisionService;

    @MockBean
    private PortalSessionInactivityFilter portalSessionInactivityFilter;

    @MockBean
    private AuditLoggingFilter auditLoggingFilter;

    @MockBean
    private com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftRejectionAudit dbtImplementationDraftRejectionAudit;

    @BeforeEach
    void preserveSecurityServicePayloadsInControllerFocusedTests() {
        IngestionAccessDecisionService boundaryService = new IngestionAccessDecisionService(
            dataSourceRepository,
            ingestionClient,
            classificationUtils,
            new com.fasterxml.jackson.databind.ObjectMapper()
        );
        when(accessDecisionService.canonicalizeTaskPayloadIdentifiers(anyMap())).thenAnswer(invocation ->
            boundaryService.canonicalizeTaskPayloadIdentifiers(invocation.getArgument(0))
        );
        when(accessDecisionService.removeInternalFilePaths(any())).thenAnswer(invocation ->
            boundaryService.removeInternalFilePaths(invocation.getArgument(0))
        );
        when(accessDecisionService.retainExplicitSecrets(any(), any())).thenAnswer(invocation ->
            boundaryService.retainExplicitSecrets(invocation.getArgument(0), invocation.getArgument(1))
        );
        when(accessDecisionService.sanitizeResponse(any())).thenAnswer(invocation ->
            boundaryService.sanitizeResponse(invocation.getArgument(0))
        );
        when(accessDecisionService.normalizeApiConnectionTest(anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
        when(accessDecisionService.requireTaskAccess(any(), org.mockito.ArgumentMatchers.anyBoolean()))
            .thenReturn(new java.util.LinkedHashMap<>());
        when(accessDecisionService.requireTaskAuthorizationAccess(any(), org.mockito.ArgumentMatchers.anyBoolean()))
            .thenReturn(new java.util.LinkedHashMap<>());
        when(accessDecisionService.listVisibleTasks(anyMap())).thenAnswer(invocation ->
            ingestionClient.listTasks(invocation.getArgument(0))
        );
        when(auditService.auditActionStrict(anyString(), any(AuditStage.class), anyString(), any()))
            .thenReturn(UUID.randomUUID());
    }

    @Test
    void createTaskForwardsPayload() throws Exception {
        String platformDataSourceId = "11111111-2222-3333-4444-555555555555";
        configureClassifiedSource(platformDataSourceId);
        DefaultDestinationSnapshot snapshot = new DefaultDestinationSnapshot(
            "rdbmswriter",
            "lake",
            Map.of("connection", java.util.List.of(Map.of("jdbcUrl", java.util.List.of("jdbc:pg")))),
            platformDataSourceId
        );
        when(destinationSyncService.ensureDefaultDestination()).thenReturn(snapshot);
        when(ingestionClient.createIngestionTask(anyMap()))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("task", "demo")));

        mockMvc.perform(post("/api/ingestion/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"task\",\"source\":{\"dataSourceId\":\"" +
                    platformDataSourceId +
                    "\"},\"classificationSeal\":{\"subjectType\":\"ASSET\",\"subjectKey\":\"" +
                    PREVIOUS_SOURCE_KEY +
                    "\"},\"fieldClassifications\":{\"legacy\":\"PUBLIC\"}," +
                    "\"destination\":{\"config\":{\"table\":[\"t1\"]}}}"
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(ingestionClient).createIngestionTask(captor.capture());
        Map<String, Object> payload = captor.getValue();
        Object destinationObj = payload.get("destination");
        assertThat(destinationObj).isInstanceOf(Map.class);
        Map<String, Object> destination = (Map<String, Object>) destinationObj;
        assertThat(destination.get("definitionId")).isEqualTo("rdbmswriter");
        Map<String, Object> config = (Map<String, Object>) destination.get("config");
        assertThat(((java.util.List<?>) config.get("table")).get(0)).isEqualTo("t1");
        assertThat(config.get("targetDataSourceId")).isEqualTo(platformDataSourceId);
        assertThat(payload)
            .containsEntry("sourceDataSourceId", platformDataSourceId)
            .containsEntry("targetDataSourceId", platformDataSourceId);
        assertThat((Map<String, Object>) payload.get("source"))
            .containsEntry("dataSourceId", platformDataSourceId);
        Map<String, Object> seal = (Map<String, Object>) payload.get("classificationSeal");
        assertThat(seal.get("effectiveLevel")).isEqualTo("SECRET");
        assertThat(seal.get("subjectKey")).isEqualTo("data-source:" + platformDataSourceId);
        assertThat((Map<String, String>) payload.get("fieldClassifications"))
            .containsEntry("identity_no", "SECRET");
        ArgumentCaptor<CatalogClassificationService.SealCommand> command =
            ArgumentCaptor.forClass(CatalogClassificationService.SealCommand.class);
        verify(classificationService).sealOrRaise(command.capture());
        assertThat(command.getValue().declaredLevel()).isEqualTo("INTERNAL");
        assertThat(command.getValue().upstreamLevels().stream().map(String::valueOf).toList())
            .containsExactly("SECRET");
        verify(classificationService, never()).resolve("ASSET", PREVIOUS_SOURCE_KEY);
    }

    @Test
    void createTaskAllowsManagedDatasourceWithoutClassificationEvidence() throws Exception {
        String sourceId = "22222222-3333-4444-5555-666666666666";
        configureUnclassifiedSource(sourceId);
        DefaultDestinationSnapshot snapshot = new DefaultDestinationSnapshot(
            "rdbmswriter",
            "lake",
            Map.of("connection", java.util.List.of(Map.of("jdbcUrl", java.util.List.of("jdbc:pg")))),
            sourceId
        );
        when(destinationSyncService.ensureDefaultDestination()).thenReturn(snapshot);
        when(ingestionClient.createIngestionTask(anyMap()))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("id", 2, "status", "active")));

        mockMvc.perform(post("/api/ingestion/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"plain-task\",\"source\":{\"dataSourceId\":\"" +
                    sourceId +
                    "\"},\"destination\":{\"config\":{\"table\":[\"t1\"]}}}"
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("active"));

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(ingestionClient).createIngestionTask(payload.capture());
        assertThat(payload.getValue()).doesNotContainKeys("classificationSeal", "fieldClassifications");
        verify(classificationService, never()).sealOrRaise(any(CatalogClassificationService.SealCommand.class));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "UNKNOWN_LEVEL" })
    void createDraftRejectsInvalidDatasourceClassificationInsteadOfTreatingItAsUnclassified(
        String classification
    ) throws Exception {
        String sourceId = "44444444-5555-6666-7777-888888888888";
        InfraDataSource source = new InfraDataSource();
        source.setId(UUID.fromString(sourceId));
        source.setName("invalid-classification-source");
        source.setProps("{\"classification\":\"" + classification + "\"}");
        when(dataSourceRepository.findById(source.getId())).thenReturn(Optional.of(source));

        mockMvc.perform(post("/api/ingestion/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"invalid-task\",\"draft\":true," +
                    "\"source\":{\"dataSourceId\":\"" + sourceId + "\"}}"
                ))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("SOURCE_CLASSIFICATION_INVALID"));

        verify(ingestionClient, never()).createIngestionTask(anyMap());
    }

    @Test
    void deleteTaskRetiresPlanWithoutDeletingTraceabilityEvidence() throws Exception {
        Map<String, Object> task = Map.of(
            "id", 19,
            "name", "finance-ods",
            "status", "deleted"
        );
        when(ingestionClient.deleteTask(19L)).thenReturn(
            new ApiResponse<>(
                200,
                "ok",
                Map.of(
                    "task", task,
                    "taskId", 19,
                    "status", "deleted",
                    "runtimeArtifactCleanup", Map.of("addaxCleaned", true, "airflowCleaned", true, "retryable", false)
                )
            )
        );

        mockMvc.perform(delete("/api/ingestion/tasks/19"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("deleted"));

        verify(accessDecisionService).requireTaskAccess(19L, true);
        verify(ingestionClient).deleteTask(19L);
        verify(auditService).auditActionStrict(
            eq("INGESTION_TASK_DELETE"),
            eq(AuditStage.SUCCESS),
            eq("19"),
            argThat(payload -> payload instanceof Map<?, ?> map && "RUNTIME_ARTIFACT_CLEANUP".equals(map.get("subOperation")))
        );
        verify(odsTableMappingSyncService, never()).removeFromIngestionPayload(anyMap());
        verify(externalRunLogService, never()).deleteIngestionRuns(any(), eq(19L));
    }

    @Test
    void deleteTaskPersistsPartialRuntimeArtifactCleanupAudit() throws Exception {
        when(ingestionClient.deleteTask(19L)).thenReturn(
            new ApiResponse<>(
                200,
                "ok",
                Map.of(
                    "taskId", 19,
                    "status", "deleted",
                    "runtimeArtifactCleanup",
                    Map.of(
                        "addaxCleaned", false,
                        "airflowCleaned", true,
                        "retryable", true,
                        "addaxErrorType", "IllegalStateException"
                    )
                )
            )
        );

        mockMvc.perform(delete("/api/ingestion/tasks/19"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.runtimeArtifactCleanup.retryable").value(true));

        verify(auditService).auditActionStrict(
            eq("INGESTION_TASK_DELETE"),
            eq(AuditStage.FAIL),
            eq("19"),
            argThat(payload ->
                payload instanceof Map<?, ?> map &&
                "RUNTIME_ARTIFACT_CLEANUP".equals(map.get("subOperation")) &&
                Boolean.TRUE.equals(map.get("retryable")) &&
                "IllegalStateException".equals(map.get("addaxErrorType"))
            )
        );
    }

    @Test
    void deleteTaskPropagatesDownstreamConflictStatus() throws Exception {
        when(ingestionClient.deleteTask(19L)).thenReturn(
            new ApiResponse<>(409, "任务正在执行，无法删除", null)
        );

        mockMvc.perform(delete("/api/ingestion/tasks/19"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));

        verify(accessDecisionService).requireTaskAccess(19L, true);
        verify(ingestionClient).deleteTask(19L);
        verify(odsTableMappingSyncService, never()).removeFromIngestionPayload(anyMap());
        verify(externalRunLogService, never()).deleteIngestionRuns(any(), eq(19L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createTaskUsesSelectedTargetDataSourceWhenProvided() throws Exception {
        String targetDataSourceId = "a0000000-0000-0000-0000-000000000001";
        String sourceDataSourceId = "11111111-2222-3333-4444-555555555555";
        configureClassifiedSource(sourceDataSourceId);
        DefaultDestinationSnapshot snapshot = new DefaultDestinationSnapshot(
            "postgresqlwriter",
            "数仓 (biadmin)",
            Map.of(
                "jdbcUrl", "jdbc:postgresql://dts-pg:5432/biadmin",
                "username", "biadmin",
                "password", "managed-default-password"
            ),
            targetDataSourceId
        );
        when(destinationSyncService.ensureDestination(targetDataSourceId)).thenReturn(snapshot);
        when(ingestionClient.createIngestionTask(anyMap()))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("task", "demo")));

        mockMvc.perform(post("/api/ingestion/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name":"task",
                      "source":{"dataSourceId":"11111111-2222-3333-4444-555555555555"},
                      "destination":{
                        "usePlatformDefault":true,
                        "config":{
                          "targetDataSourceId":"a0000000-0000-0000-0000-000000000001",
                          "connection":[{"table":["ods_orders"]}]
                        }
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(ingestionClient).createIngestionTask(captor.capture());
        Map<String, Object> destination = (Map<String, Object>) captor.getValue().get("destination");
        Map<String, Object> config = (Map<String, Object>) destination.get("config");
        assertThat(destination.get("definitionId")).isEqualTo("postgresqlwriter");
        assertThat(config.get("targetDataSourceId")).isEqualTo(targetDataSourceId);
        assertThat(config.get("jdbcUrl")).isEqualTo("jdbc:postgresql://dts-pg:5432/biadmin");
        assertThat(config.get("username")).isEqualTo("biadmin");
        assertThat(config).doesNotContainKey("password");
    }

    @Test
    void createTaskRejectsExplicitManagedDestinationPassword() throws Exception {
        String targetDataSourceId = "a0000000-0000-0000-0000-000000000001";
        String sourceDataSourceId = "11111111-2222-3333-4444-555555555555";
        configureClassifiedSource(sourceDataSourceId);
        when(destinationSyncService.ensureDestination(targetDataSourceId))
            .thenReturn(
                new DefaultDestinationSnapshot(
                    "postgresqlwriter",
                    "数仓 (biadmin)",
                    Map.of("jdbcUrl", "jdbc:postgresql://dts-pg:5432/biadmin"),
                    targetDataSourceId
                )
            );

        mockMvc.perform(post("/api/ingestion/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name":"task-with-raw-password",
                      "source":{"dataSourceId":"11111111-2222-3333-4444-555555555555"},
                      "destination":{
                        "usePlatformDefault":true,
                        "config":{
                          "targetDataSourceId":"a0000000-0000-0000-0000-000000000001",
                          "password":"must-not-be-forwarded"
                        }
                      }
                    }
                    """
                ))
            .andExpect(status().isBadRequest());

        verify(ingestionClient, never()).createIngestionTask(anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fileTaskUsesCompositeSealWithFileFloorAndHighestFieldClassification() throws Exception {
        DefaultDestinationSnapshot destination = new DefaultDestinationSnapshot(
            "rdbmswriter",
            "lake",
            Map.of("connection", java.util.List.of(Map.of("jdbcUrl", java.util.List.of("jdbc:pg")))),
            "11111111-2222-3333-4444-555555555555"
        );
        when(destinationSyncService.ensureDefaultDestination()).thenReturn(destination);
        when(ingestionClient.createIngestionTask(anyMap()))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("task", "file-demo")));

        UUID fileSealId = UUID.randomUUID();
        CatalogClassificationSnapshot fileSnapshot = snapshot(
            fileSealId,
            "FILE",
            "ingestion-upload:file-001",
            "SECRET"
        );
        CatalogClassificationSnapshot taskSnapshot = snapshot(
            UUID.randomUUID(),
            "ASSET",
            "ingestion-file:" + fileSnapshot.getSubjectKey(),
            "CONFIDENTIAL"
        );
        when(classificationService.resolve("FILE", fileSnapshot.getSubjectKey()))
            .thenReturn(java.util.Optional.of(fileSnapshot));
        when(classificationService.sealOrRaise(any(CatalogClassificationService.SealCommand.class)))
            .thenReturn(taskSnapshot);

        mockMvc.perform(post("/api/ingestion/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name":"file-task",
                      "source":{"config":{
                        "classificationSeal":{
                          "sealId":"%s",
                          "subjectType":"FILE",
                          "subjectKey":"%s",
                          "snapshotVersion":1,
                          "checksum":"%s"
                        },
                        "_fileId":"file-001",
                        "_fileHash":"%s",
                        "_encrypted":true,
                        "_fileColumns":[{"name":"name"},{"name":"identity_no"}],
                        "fieldClassifications":{
                          "name":"SECRET",
                          "identity_no":"CONFIDENTIAL"
                        }
                      }},
                      "destination":{"config":{"table":["ods_file"]}}
                    }
                    """.formatted(
                        fileSealId,
                        fileSnapshot.getSubjectKey(),
                        fileSnapshot.getEvidenceChecksum(),
                        fileSnapshot.getEvidenceChecksum()
                    )
                ))
            .andExpect(status().isOk());

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(ingestionClient).createIngestionTask(payload.capture());
        Map<String, Object> seal = (Map<String, Object>) payload.getValue().get("classificationSeal");
        assertThat(seal)
            .containsEntry("subjectType", "ASSET")
            .containsEntry("effectiveLevel", "CONFIDENTIAL")
            .containsEntry("fileFloor", "SECRET")
            .containsEntry("fileChecksum", fileSnapshot.getEvidenceChecksum());
        assertThat((Map<String, String>) payload.getValue().get("fieldClassifications"))
            .containsEntry("name", "SECRET")
            .containsEntry("identity_no", "CONFIDENTIAL");

        ArgumentCaptor<CatalogClassificationService.SealCommand> command =
            ArgumentCaptor.forClass(CatalogClassificationService.SealCommand.class);
        verify(classificationService, times(3)).sealOrRaise(command.capture());
        assertThat(command.getAllValues())
            .filteredOn(item -> "COLUMN".equals(item.subjectType()))
            .extracting(CatalogClassificationService.SealCommand::declaredLevel)
            .containsExactlyInAnyOrder("SECRET", "CONFIDENTIAL");
        CatalogClassificationService.SealCommand taskCommand = command
            .getAllValues()
            .stream()
            .filter(item -> "ASSET".equals(item.subjectType()))
            .findFirst()
            .orElseThrow();
        assertThat(taskCommand.declaredLevel()).isEqualTo("SECRET");
        assertThat(taskCommand.upstreamLevels().stream().map(String::valueOf).toList())
            .containsExactlyInAnyOrder("SECRET", "CONFIDENTIAL");
    }

    @Test
    void fileTaskRejectsFieldClassificationBelowTheFileFloorBeforeForwarding() throws Exception {
        DefaultDestinationSnapshot destination = new DefaultDestinationSnapshot(
            "rdbmswriter",
            "lake",
            Map.of("connection", java.util.List.of(Map.of("jdbcUrl", java.util.List.of("jdbc:pg")))),
            "11111111-2222-3333-4444-555555555555"
        );
        when(destinationSyncService.ensureDefaultDestination()).thenReturn(destination);

        UUID fileSealId = UUID.randomUUID();
        CatalogClassificationSnapshot fileSnapshot = snapshot(
            fileSealId,
            "FILE",
            "ingestion-upload:file-001",
            "SECRET"
        );
        when(classificationService.resolve("FILE", fileSnapshot.getSubjectKey()))
            .thenReturn(java.util.Optional.of(fileSnapshot));

        mockMvc.perform(post("/api/ingestion/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name":"file-task",
                      "source":{"config":{
                        "classificationSeal":{
                          "sealId":"%s",
                          "subjectType":"FILE",
                          "subjectKey":"%s",
                          "snapshotVersion":1,
                          "checksum":"%s"
                        },
                        "_fileId":"file-001",
                        "_fileHash":"%s",
                        "_encrypted":true,
                        "fieldClassifications":{"name":"PUBLIC"},
                        "_fileColumns":[{"name":"name"}]
                      }},
                      "destination":{"config":{"table":["ods_file"]}}
                    }
                    """.formatted(
                        fileSealId,
                        fileSnapshot.getSubjectKey(),
                        fileSnapshot.getEvidenceChecksum(),
                        fileSnapshot.getEvidenceChecksum()
                    )
                ))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CLASSIFICATION_DOWNGRADE_FORBIDDEN"));

        verify(ingestionClient, never()).createIngestionTask(anyMap());
    }

    @Test
    void fileTaskCannotBypassFileSealWithTopLevelAssetSeal() throws Exception {
        DefaultDestinationSnapshot destination = new DefaultDestinationSnapshot(
            "rdbmswriter",
            "lake",
            Map.of("connection", java.util.List.of(Map.of("jdbcUrl", java.util.List.of("jdbc:pg")))),
            "11111111-2222-3333-4444-555555555555"
        );
        when(destinationSyncService.ensureDefaultDestination()).thenReturn(destination);
        when(ingestionClient.createIngestionTask(anyMap()))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("task", "should-not-forward")));

        mockMvc.perform(post("/api/ingestion/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name":"file-task-without-file-seal",
                      "sourceType":"txtfilereader",
                      "sourceConfig":{
                        "_fileId":"file-001",
                        "_fileHash":"abcdef",
                        "_encrypted":true
                      },
                      "classificationSeal":{
                        "sealId":"11111111-2222-3333-4444-555555555555",
                        "subjectType":"ASSET",
                        "subjectKey":"ingestion-file:forged",
                        "snapshotVersion":1,
                        "checksum":"abcdef",
                        "effectiveLevel":"PUBLIC"
                      },
                      "fieldClassifications":{},
                      "destination":{"config":{"table":["ods_file"]}}
                    }
                    """
                ))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("FILE_CLASSIFICATION_SEAL_REQUIRED"));

        verify(ingestionClient, never()).createIngestionTask(anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void providedOdsSealIsReplacedByCurrentManagedSourceAndRaisedBeforeForwarding() throws Exception {
        DefaultDestinationSnapshot destination = new DefaultDestinationSnapshot(
            "rdbmswriter",
            "lake",
            Map.of("connection", java.util.List.of(Map.of("jdbcUrl", java.util.List.of("jdbc:pg")))),
            "11111111-2222-3333-4444-555555555555"
        );
        when(destinationSyncService.ensureDefaultDestination()).thenReturn(destination);
        when(ingestionClient.createIngestionTask(anyMap()))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("task", "ods-demo")));

        String sourceId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        configureClassifiedSource(sourceId);
        CatalogClassificationSnapshot providedSnapshot = snapshot(
            UUID.randomUUID(),
            "ASSET",
            "data-source:" + sourceId,
            "INTERNAL"
        );
        CatalogClassificationSnapshot taskSnapshot = snapshot(
            UUID.randomUUID(),
            "ASSET",
            "data-source:" + sourceId,
            "SECRET"
        );
        when(classificationService.sealOrRaise(any(CatalogClassificationService.SealCommand.class)))
            .thenReturn(taskSnapshot);

        mockMvc.perform(post("/api/ingestion/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name":"ods-task",
                      "sourceType":"mysqlreader",
                      "sourceDataSourceId":"%s",
                      "classificationSeal":{
                        "sealId":"%s",
                        "subjectType":"ASSET",
                        "subjectKey":"%s",
                        "snapshotVersion":1,
                        "checksum":"%s"
                      },
                      "fieldClassifications":{"identity_no":"SECRET"},
                      "destination":{"config":{"table":["ods_customer"]}}
                    }
                    """.formatted(
                        sourceId,
                        providedSnapshot.getId(),
                        providedSnapshot.getSubjectKey(),
                        providedSnapshot.getEvidenceChecksum()
                    )
                ))
            .andExpect(status().isOk());

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(ingestionClient).createIngestionTask(payload.capture());
        assertThat((Map<String, Object>) payload.getValue().get("classificationSeal"))
            .containsEntry("effectiveLevel", "SECRET")
            .containsEntry("subjectKey", "data-source:" + sourceId);
        ArgumentCaptor<CatalogClassificationService.SealCommand> command =
            ArgumentCaptor.forClass(CatalogClassificationService.SealCommand.class);
        verify(classificationService).sealOrRaise(command.capture());
        assertThat(command.getValue().declaredLevel()).isEqualTo("INTERNAL");
        assertThat(command.getValue().upstreamLevels().stream().map(String::valueOf).toList())
            .containsExactly("SECRET");
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateTaskReplacesPreviousSourceSealWithCurrentManagedSourceSeal() throws Exception {
        String currentSourceId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        String previousSourceKey = "data-source:11111111-2222-3333-4444-555555555555";
        configureClassifiedSource(currentSourceId);
        when(destinationSyncService.ensureDefaultDestination())
            .thenReturn(
                new DefaultDestinationSnapshot(
                    "postgresqlwriter",
                    "lake",
                    Map.of("jdbcUrl", "jdbc:postgresql://dts-pg:5432/biadmin"),
                    "99999999-2222-3333-4444-555555555555"
                )
            );
        when(ingestionClient.updateTask(org.mockito.ArgumentMatchers.eq(9L), anyMap()))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("id", 9)));

        mockMvc.perform(put("/api/ingestion/tasks/9")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name":"moved-source",
                      "sourceType":"mysqlreader",
                      "sourceDataSourceId":"%s",
                      "source":{
                        "dataSourceId":"%s",
                        "config":{
                          "classificationSeal":{"subjectKey":"%s"},
                          "fieldClassifications":{"nested_legacy":"PUBLIC"}
                        }
                      },
                      "sourceConfig":{
                        "classificationSeal":{"subjectKey":"%s"},
                        "fieldClassifications":{"top_legacy":"PUBLIC"}
                      },
                      "classificationSeal":{
                        "sealId":"11111111-2222-3333-4444-555555555555",
                        "subjectType":"ASSET",
                        "subjectKey":"%s",
                        "snapshotVersion":1,
                        "checksum":"old"
                      },
                      "fieldClassifications":{"legacy_field":"PUBLIC"},
                      "destinationConfig":{"table":["ods_customer"]}
                    }
                    """.formatted(currentSourceId, currentSourceId, previousSourceKey, previousSourceKey, previousSourceKey)
                ))
            .andExpect(status().isOk());

        ArgumentCaptor<Map<String, Object>> forwarded = ArgumentCaptor.forClass(Map.class);
        verify(ingestionClient).updateTask(org.mockito.ArgumentMatchers.eq(9L), forwarded.capture());
        Map<String, Object> seal = (Map<String, Object>) forwarded.getValue().get("classificationSeal");
        assertThat(seal.get("subjectKey")).isEqualTo("data-source:" + currentSourceId);
        assertThat((Map<String, String>) forwarded.getValue().get("fieldClassifications"))
            .containsExactly(Map.entry("identity_no", "SECRET"));
        assertThat((Map<String, Object>) ((Map<String, Object>) forwarded.getValue().get("source")).get("config"))
            .doesNotContainKeys("classificationSeal", "fieldClassifications");
        assertThat((Map<String, Object>) forwarded.getValue().get("sourceConfig"))
            .doesNotContainKeys("classificationSeal", "fieldClassifications");
        verify(classificationService, never()).resolve("ASSET", previousSourceKey);
    }

    @Test
    void createRejectsConflictingTopLevelAndNestedSourceBeforeForwarding() throws Exception {
        String ownSourceId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        String foreignNestedId = "11111111-2222-3333-4444-555555555555";

        mockMvc.perform(post("/api/ingestion/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name":"conflicting-source",
                      "sourceDataSourceId":"%s",
                      "source":{"dataSourceId":"%s"},
                      "destination":{"config":{"table":["ods_customer"]}}
                    }
                    """.formatted(ownSourceId, foreignNestedId)
                ))
            .andExpect(status().isBadRequest());

        verify(ingestionClient, never()).createIngestionTask(anyMap());
    }

    @Test
    void updateRejectsConflictingTargetAliasesBeforeReadingOrForwardingTask() throws Exception {
        String ownTargetId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        String foreignNestedId = "11111111-2222-3333-4444-555555555555";

        mockMvc.perform(put("/api/ingestion/tasks/91")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name":"conflicting-target",
                      "targetDataSourceId":"%s",
                      "destinationConfig":{"targetDataSourceId":"%s"}
                    }
                    """.formatted(ownTargetId, foreignNestedId)
                ))
            .andExpect(status().isBadRequest());

        verify(accessDecisionService, never()).requireTaskAccess(
            org.mockito.ArgumentMatchers.eq(91L),
            org.mockito.ArgumentMatchers.anyBoolean()
        );
        verify(ingestionClient, never()).updateTask(org.mockito.ArgumentMatchers.eq(91L), anyMap());
    }

    @Test
    void fileTaskClearanceGateCoversDetailUpdateAdmissionExecutionStagingAndErrorDownload() throws Exception {
        long taskId = 77L;
        Map<String, Object> highFileTask = Map.of(
            "id", taskId,
            "sourceType", "excelreader",
            "sourceConfig", Map.of(
                "_fileId", "file-77",
                "classificationSeal", Map.of("effectiveLevel", "SECRET", "fileFloor", "SECRET")
            )
        );
        org.springframework.web.server.ResponseStatusException forbidden = new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.FORBIDDEN,
            "文件任务密级超出当前用户密级"
        );
        when(ingestionClient.getTask(taskId)).thenReturn(new ApiResponse<>(200, "ok", highFileTask));
        org.mockito.Mockito.doThrow(forbidden)
            .when(accessDecisionService)
            .requireTaskPayloadAccess(org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.anyBoolean());
        when(accessDecisionService.requireTaskAccess(
                org.mockito.ArgumentMatchers.eq(taskId),
                org.mockito.ArgumentMatchers.anyBoolean()
            ))
            .thenThrow(forbidden);
        when(accessDecisionService.requireTaskAuthorizationAccess(taskId, true)).thenThrow(forbidden);

        mockMvc.perform(get("/api/ingestion/tasks/{id}", taskId)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/ingestion/tasks/{id}", taskId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"blocked\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ingestion/tasks/{id}/admit", taskId)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ingestion/tasks/{id}/execute", taskId)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ingestion/tasks/{id}/staging/errors/summary", taskId)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ingestion/tasks/{id}/staging/errors/download", taskId)).andExpect(status().isForbidden());

        verify(ingestionClient, never()).updateTask(org.mockito.ArgumentMatchers.eq(taskId), anyMap());
        verify(ingestionClient, never()).admitTask(org.mockito.ArgumentMatchers.eq(taskId), anyMap());
        verify(ingestionClient, never()).executeTask(taskId);
        verify(ingestionClient, never()).getStagingErrorSummary(org.mockito.ArgumentMatchers.eq(taskId), anyMap());
        verify(ingestionClient, never()).downloadStagingErrors(taskId);
    }

    @Test
    void ordinaryTaskReadEndpointsApplySourceClearanceGateBeforeReturningConfiguration() throws Exception {
        long taskId = 78L;
        Map<String, Object> databaseTask = Map.of(
            "id", taskId,
            "sourceType", "mysqlreader",
            "sourceDataSourceId", "11111111-2222-3333-4444-555555555555",
            "destinationConfig", Map.of("targetDataSourceId", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
        );
        org.springframework.web.server.ResponseStatusException forbidden = new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.FORBIDDEN,
            "数据源密级超出当前用户密级"
        );
        when(ingestionClient.getTask(taskId)).thenReturn(new ApiResponse<>(200, "ok", databaseTask));
        org.mockito.Mockito.doThrow(forbidden)
            .when(accessDecisionService)
            .requireTaskPayloadAccess(org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.eq(false));
        when(accessDecisionService.requireTaskAccess(taskId, false)).thenThrow(forbidden);

        mockMvc.perform(get("/api/ingestion/tasks/{id}", taskId)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ingestion/tasks/{id}/revisions", taskId)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ingestion/tasks/{id}/effective-config", taskId)).andExpect(status().isForbidden());

        verify(ingestionClient, never()).getTaskRevisions(taskId);
        verify(ingestionClient, never()).getTaskEffectiveConfig(taskId);
    }

    @Test
    void taskResponsesRecursivelyRemoveInternalFilePathsButPreserveApiResourcePathAndSeal() throws Exception {
        Map<String, Object> leaked = Map.of(
            "fileId", "file-1",
            "hostPath", "/srv/upload.xlsx",
            "addaxJobPath", new String[] { "/opt/airflow/jobs/task.json" },
            "nested", Map.of(
                "containerPath", "/decrypted/upload.xlsx",
                "_file_path", "/tmp/upload.xlsx",
                "path", new String[] { "/decrypted/upload.xlsx" }
            ),
            "classificationSeal", Map.of("effectiveLevel", "SECRET", "fileFloor", "SECRET"),
            "resource", Map.of("resourceId", "customers", "path", "/tmp/customers")
        );
        when(ingestionClient.listTasks(anyMap()))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("content", java.util.List.of(leaked))));
        when(ingestionClient.getTask(81L)).thenReturn(new ApiResponse<>(200, "ok", leaked));
        when(ingestionClient.getTaskRevisions(81L)).thenReturn(new ApiResponse<>(200, "ok", java.util.List.of(leaked)));
        when(ingestionClient.getTaskEffectiveConfig(81L)).thenReturn(new ApiResponse<>(200, "ok", leaked));
        when(ingestionClient.executeTask(81L)).thenReturn(new ApiResponse<>(200, "ok", leaked));
        when(ingestionClient.getExecutionLog(org.mockito.ArgumentMatchers.eq(81L), org.mockito.ArgumentMatchers.eq(2L), anyMap()))
            .thenReturn(new ApiResponse<>(200, "ok", leaked));
        when(ingestionClient.getGovernanceOverview(anyMap())).thenReturn(new ApiResponse<>(200, "ok", leaked));
        when(ingestionClient.listChangeLogs(anyMap())).thenReturn(new ApiResponse<>(200, "ok", Map.of("content", java.util.List.of(leaked))));
        when(ingestionClient.listTemplates()).thenReturn(new ApiResponse<>(200, "ok", java.util.List.of(leaked)));

        mockMvc.perform(get("/api/ingestion/tasks/list"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].hostPath").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].nested.containerPath").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].resource.path").value("/tmp/customers"));
        mockMvc.perform(get("/api/ingestion/tasks/81"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.hostPath").doesNotExist())
            .andExpect(jsonPath("$.data.fileId").value("file-1"))
            .andExpect(jsonPath("$.data.classificationSeal.effectiveLevel").value("SECRET"));
        mockMvc.perform(get("/api/ingestion/tasks/81/revisions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].hostPath").doesNotExist());
        mockMvc.perform(get("/api/ingestion/tasks/81/effective-config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nested._file_path").doesNotExist())
            .andExpect(jsonPath("$.data.addaxJobPath").doesNotExist());
        mockMvc.perform(post("/api/ingestion/tasks/81/execute"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.hostPath").doesNotExist());
        mockMvc.perform(get("/api/ingestion/tasks/81/executions/2/logs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nested.containerPath").doesNotExist())
            .andExpect(jsonPath("$.data.resource.path").value("/tmp/customers"));
        mockMvc.perform(get("/api/ingestion/tasks/executions/governance-overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.addaxJobPath").doesNotExist());
        mockMvc.perform(get("/api/ingestion/tasks/changes").param("taskId", "81"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].nested.path").doesNotExist());
        mockMvc.perform(get("/api/ingestion/templates"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].hostPath").doesNotExist())
            .andExpect(jsonPath("$.data[0].resource.path").value("/tmp/customers"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateWithoutPasswordDoesNotReadOrForwardStoredOrDefaultPasswords() throws Exception {
        String targetId = "99999999-2222-3333-4444-555555555555";
        when(destinationSyncService.ensureDestination(targetId))
            .thenReturn(
                new DefaultDestinationSnapshot(
                    "postgresqlwriter",
                    "lake",
                    Map.of(
                        "jdbcUrl", "jdbc:postgresql://dts-pg:5432/biadmin",
                        "password", "default-password",
                        "connection", List.of(Map.of("passwd", "nested-password"))
                    ),
                    targetId
                )
            );
        when(ingestionClient.updateTask(org.mockito.ArgumentMatchers.eq(92L), anyMap()))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("id", 92L)));

        mockMvc.perform(put("/api/ingestion/tasks/92")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name":"passwordless-update",
                      "targetDataSourceId":"%s",
                      "destinationConfig":{"targetDataSourceId":"%s","table":["ods_demo"]}
                    }
                    """.formatted(targetId, targetId)
                ))
            .andExpect(status().isOk());

        ArgumentCaptor<Map<String, Object>> forwarded = ArgumentCaptor.forClass(Map.class);
        verify(ingestionClient).updateTask(org.mockito.ArgumentMatchers.eq(92L), forwarded.capture());
        Map<String, Object> destination = (Map<String, Object>) forwarded.getValue().get("destinationConfig");
        assertThat(destination).doesNotContainKeys("password", "passwd");
        assertThat((Map<String, Object>) ((List<?>) destination.get("connection")).get(0))
            .doesNotContainKeys("password", "passwd");
        verify(accessDecisionService).requireTaskAuthorizationAccess(92L, true);
        verify(ingestionClient, never()).getTask(92L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void oneExplicitPasswordDoesNotReleaseOtherDefaultSecretsDuringUpdate() throws Exception {
        String targetId = "88888888-2222-3333-4444-555555555555";
        when(destinationSyncService.ensureDestination(targetId))
            .thenReturn(
                new DefaultDestinationSnapshot(
                    "postgresqlwriter",
                    "lake",
                    Map.of(
                        "jdbcUrl", "jdbc:postgresql://dts-pg:5432/biadmin",
                        "password", "default-password",
                        "clientSecret", "default-client-secret",
                        "connection", List.of(Map.of(
                            "password", "nested-password",
                            "apiKey", "nested-api-key"
                        ))
                    ),
                    targetId
                )
            );
        when(ingestionClient.updateTask(org.mockito.ArgumentMatchers.eq(93L), anyMap()))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("id", 93L)));

        mockMvc.perform(put("/api/ingestion/tasks/93")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name":"single-explicit-secret",
                      "targetDataSourceId":"%s",
                      "destinationConfig":{
                        "targetDataSourceId":"%s",
                        "password":"request-password",
                        "table":["ods_demo"]
                      }
                    }
                    """.formatted(targetId, targetId)
                ))
            .andExpect(status().isOk());

        ArgumentCaptor<Map<String, Object>> forwarded = ArgumentCaptor.forClass(Map.class);
        verify(ingestionClient).updateTask(org.mockito.ArgumentMatchers.eq(93L), forwarded.capture());
        Map<String, Object> destination = (Map<String, Object>) forwarded.getValue().get("destinationConfig");
        Map<String, Object> connection = (Map<String, Object>) ((List<?>) destination.get("connection")).get(0);
        assertThat(destination)
            .containsEntry("password", "request-password")
            .doesNotContainKey("clientSecret");
        assertThat(connection).doesNotContainKeys("password", "apiKey");
        verify(ingestionClient, never()).getTask(93L);
    }

    @Test
    void taskReadEndpointsRecursivelyRedactSecretsAndKeepSealChecksums() throws Exception {
        Map<String, Object> leaked = new LinkedHashMap<>();
        leaked.put("id", 81L);
        leaked.put("password", "password");
        leaked.put("clientSecret", "client-secret");
        leaked.put("token", "token");
        leaked.put("secureProps", Map.of("apiKey", "api-key"));
        leaked.put("nested", Map.of("Authorization", "Bearer token", "credential", "credential"));
        leaked.put("classificationSeal", Map.of("effectiveLevel", "SECRET", "checksum", "seal-checksum"));
        when(ingestionClient.listTasks(anyMap()))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("content", List.of(leaked))));
        when(ingestionClient.getTask(81L)).thenReturn(new ApiResponse<>(200, "ok", leaked));
        when(ingestionClient.getTaskRevisions(81L)).thenReturn(new ApiResponse<>(200, "ok", List.of(leaked)));
        when(ingestionClient.getTaskEffectiveConfig(81L)).thenReturn(new ApiResponse<>(200, "ok", leaked));
        when(ingestionClient.listExecutions(org.mockito.ArgumentMatchers.eq(81L), anyMap()))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("content", List.of(leaked))));
        when(ingestionClient.getExecutionLog(org.mockito.ArgumentMatchers.eq(81L), org.mockito.ArgumentMatchers.eq(2L), anyMap()))
            .thenReturn(new ApiResponse<>(200, "ok", leaked));

        mockMvc.perform(get("/api/ingestion/tasks/list"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].password").doesNotExist());
        mockMvc.perform(get("/api/ingestion/tasks/81"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.clientSecret").doesNotExist())
            .andExpect(jsonPath("$.data.classificationSeal.checksum").value("seal-checksum"));
        mockMvc.perform(get("/api/ingestion/tasks/81/revisions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].token").doesNotExist());
        mockMvc.perform(get("/api/ingestion/tasks/81/effective-config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.secureProps").doesNotExist());
        mockMvc.perform(get("/api/ingestion/tasks/81/executions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].nested.Authorization").doesNotExist());
        mockMvc.perform(get("/api/ingestion/tasks/81/executions/2/logs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nested.credential").doesNotExist());
    }

    @Test
    void listTasksForwardsAllAccessWorkspaceFilters() throws Exception {
        Map<String, Object> expected = new java.util.LinkedHashMap<>();
        expected.put("sourceKind", "DATABASE");
        expected.put("query", "customer");
        expected.put("health", "HEALTHY");
        expected.put("status", "ACTIVE");
        expected.put("sourceDataSourceId", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        expected.put("page", "0");
        expected.put("size", "20");
        expected.put("sort", java.util.List.of("updatedAt,desc", "id,asc"));
        when(ingestionClient.listTasks(expected))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("items", java.util.List.of())));

        mockMvc.perform(get("/api/ingestion/tasks/list")
                .param("sourceKind", "DATABASE")
                .param("query", "customer")
                .param("health", "HEALTHY")
                .param("status", "ACTIVE")
                .param("sourceDataSourceId", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
                .param("page", "0")
                .param("size", "20")
                .param("sort", "updatedAt,desc", "id,asc"))
            .andExpect(status().isOk());

        verify(ingestionClient).listTasks(expected);
    }

    @Test
    void accessDefaultsAndTaskRevisionContractsAreExposedViaPlatformProxy() throws Exception {
        when(ingestionClient.getAccessDefaultPolicy())
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("policyKey", "INGESTION_DEFAULT", "version", 3)));
        when(ingestionClient.getTaskRevisions(7L))
            .thenReturn(new ApiResponse<>(200, "ok", java.util.List.of(Map.of("revisionNumber", 2, "revisionState", "ACTIVE"))));
        when(ingestionClient.getTaskEffectiveConfig(7L))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("taskId", 7, "revisionNumber", 3, "sourceKind", "DATABASE")));

        mockMvc.perform(get("/api/ingestion/access/default-policy"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.policyKey").value("INGESTION_DEFAULT"))
            .andExpect(jsonPath("$.data.version").value(3));
        mockMvc.perform(get("/api/ingestion/tasks/7/revisions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].revisionNumber").value(2));
        mockMvc.perform(get("/api/ingestion/tasks/7/effective-config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sourceKind").value("DATABASE"));
    }

    @Test
    void retryExecutionIsExposedViaPlatformProxy() throws Exception {
        when(ingestionClient.retryExecution(1L, 2L, Map.of("mode", "FAILED_ONLY")))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("executionId", 2, "status", "QUEUED")));

        mockMvc.perform(post("/api/ingestion/tasks/1/executions/2/retry").param("mode", "FAILED_ONLY"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.executionId").value(2))
            .andExpect(jsonPath("$.data.status").value("QUEUED"));

        verify(ingestionClient).retryExecution(1L, 2L, Map.of("mode", "FAILED_ONLY"));
        verify(auditService).auditActionStrict(
            eq("INGESTION_EXECUTION_RETRY"),
            eq(AuditStage.SUCCESS),
            eq("2"),
            argThat(this::isSafeTaskAuditPayload)
        );
    }

    @Test
    void successfulRemoteRetryIsNotReclassifiedAsFailWhenTerminalAuditFails() throws Exception {
        UUID beginReceipt = UUID.randomUUID();
        when(ingestionClient.retryExecution(1L, 2L, Map.of("mode", "FAILED_ONLY")))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("executionId", 2, "status", "QUEUED")));
        when(auditService.auditActionStrict(anyString(), any(AuditStage.class), anyString(), any()))
            .thenAnswer(invocation -> {
                AuditStage stage = invocation.getArgument(1);
                if (stage == AuditStage.SUCCESS) {
                    throw new IllegalStateException("audit outbox unavailable");
                }
                return beginReceipt;
            });

        mockMvc.perform(post("/api/ingestion/tasks/1/executions/2/retry").param("mode", "FAILED_ONLY"))
            .andExpect(status().isConflict());

        verify(ingestionClient, times(1)).retryExecution(1L, 2L, Map.of("mode", "FAILED_ONLY"));
        verify(auditService, never()).auditActionStrict(
            eq("INGESTION_EXECUTION_RETRY"),
            eq(AuditStage.FAIL),
            eq("2"),
            any()
        );
    }

    @Test
    void apiConnectionTestIsExposedViaPlatformProxy() throws Exception {
        when(ingestionClient.testApiConnection(Map.of("dataSourceId", "11111111-2222-3333-4444-555555555555")))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("connected", true, "httpStatus", 200, "sampleCount", 1)));

        mockMvc.perform(post("/api/ingestion/api/test-connection")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dataSourceId\":\"11111111-2222-3333-4444-555555555555\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.connected").value(true))
            .andExpect(jsonPath("$.data.httpStatus").value(200))
            .andExpect(jsonPath("$.data.sampleCount").value(1));

        verify(ingestionClient).testApiConnection(Map.of("dataSourceId", "11111111-2222-3333-4444-555555555555"));
    }

    @Test
    void executeTaskAsyncIsExposedViaPlatformProxy() throws Exception {
        when(ingestionClient.executeTaskAsync(5L))
            .thenReturn(new ApiResponse<>(202, "accepted", Map.of("taskId", 5, "status", "submitted", "async", true)));

        mockMvc.perform(post("/api/ingestion/tasks/5/execute/async"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.taskId").value(5))
            .andExpect(jsonPath("$.data.status").value("submitted"))
            .andExpect(jsonPath("$.data.async").value(true));

        verify(ingestionClient).executeTaskAsync(5L);
    }

    @Test
    void backfillTaskIsExposedViaPlatformProxy() throws Exception {
        when(ingestionClient.backfillTask(
                5L,
                Map.of(
                    "windowStart", "2026-04-29T00:00:00Z",
                    "windowEnd", "2026-04-30T00:00:00Z",
                    "column", "update_time"
                )
            ))
            .thenReturn(
                new ApiResponse<>(
                    202,
                    "补数已提交，正在后台执行",
                    Map.of("taskId", 5, "executionId", 9, "status", "submitted", "async", true)
                )
            );

        mockMvc.perform(post("/api/ingestion/tasks/5/backfill")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"windowStart\":\"2026-04-29T00:00:00Z\",\"windowEnd\":\"2026-04-30T00:00:00Z\",\"column\":\"update_time\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.taskId").value(5))
            .andExpect(jsonPath("$.data.executionId").value(9))
            .andExpect(jsonPath("$.data.status").value("submitted"))
            .andExpect(jsonPath("$.data.async").value(true));

        verify(ingestionClient).backfillTask(
            5L,
            Map.of(
                "windowStart", "2026-04-29T00:00:00Z",
                "windowEnd", "2026-04-30T00:00:00Z",
                "column", "update_time"
            )
        );
    }

    @Test
    void retryExecutionAsyncIsExposedViaPlatformProxy() throws Exception {
        when(ingestionClient.retryExecutionAsync(1L, 2L, Map.of("mode", "FAILED_ONLY")))
            .thenReturn(new ApiResponse<>(202, "accepted", Map.of("taskId", 1, "executionId", 2, "status", "submitted", "async", true)));

        mockMvc.perform(post("/api/ingestion/tasks/1/executions/2/retry/async").param("mode", "FAILED_ONLY"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.taskId").value(1))
            .andExpect(jsonPath("$.data.executionId").value(2))
            .andExpect(jsonPath("$.data.status").value("submitted"))
            .andExpect(jsonPath("$.data.async").value(true));

        verify(ingestionClient).retryExecutionAsync(1L, 2L, Map.of("mode", "FAILED_ONLY"));
    }

    @Test
    void uploadAndParseRequiresFileClassificationBeforeForwarding() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "customers.csv",
            "text/csv",
            "name,identity_no\nAlice,110101".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/ingestion/files/upload-and-parse").file(file))
            .andExpect(status().isBadRequest());

        verify(ingestionClient, never()).uploadAndParse(any(), any(), any(), any());
    }

    @Test
    void legacyFileUploadAndParseEndpointsAreGone() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "customers.csv",
            "text/csv",
            "name\nAlice".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/ingestion/files/upload").file(file))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.status").value(410));
        mockMvc.perform(post("/api/ingestion/files/parse")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fileId\":\"file-1\"}"))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.status").value(410));

        verify(ingestionClient, never()).uploadFile(any());
        verify(ingestionClient, never()).parseUploadedFile(any(), any(), any(), any(), any());
    }

    @Test
    void uploadAndParseRejectsMissingUserClearanceBeforeForwarding() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "customers.csv",
            "text/csv",
            "name,identity_no\nAlice,110101".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        when(classificationUtils.getCurrentUserExplicitMaxLevel()).thenReturn(Optional.empty());

        mockMvc.perform(multipart("/api/ingestion/files/upload-and-parse")
                .file(file)
                .param("classification", "INTERNAL"))
            .andExpect(status().isForbidden());

        verify(ingestionClient, never()).uploadAndParse(any(), any(), any(), any());
    }

    @Test
    void uploadAndParseRejectsClassificationAboveUserClearanceBeforeForwarding() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "customers.csv",
            "text/csv",
            "name,identity_no\nAlice,110101".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        when(classificationUtils.getCurrentUserExplicitMaxLevel()).thenReturn(Optional.of("INTERNAL"));

        mockMvc.perform(multipart("/api/ingestion/files/upload-and-parse")
                .file(file)
                .param("classification", "CONFIDENTIAL"))
            .andExpect(status().isForbidden());

        verify(ingestionClient, never()).uploadAndParse(any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadAndParseSealsEncryptedUploadAndReturnsFieldFloors() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "customers.csv",
            "text/csv",
            "name,identity_no\nAlice,110101".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        Map<String, Object> upstreamData = new java.util.LinkedHashMap<>();
        upstreamData.put("fileId", "file-001");
        upstreamData.put("fileHash", "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789");
        upstreamData.put("originalName", "customers.csv");
        upstreamData.put("encrypted", true);
        upstreamData.put(
            "columns",
            java.util.List.of(
                Map.of("name", "name", "type", "string"),
                Map.of("name", "identity_no", "type", "string")
            )
        );
        when(ingestionClient.uploadAndParse(any(), any(), any(), any()))
            .thenReturn(new ApiResponse<>(200, "ok", upstreamData));
        when(classificationUtils.getCurrentUserExplicitMaxLevel()).thenReturn(Optional.of("SECRET"));
        CatalogClassificationSnapshot fileSnapshot = snapshot(
            UUID.randomUUID(),
            "FILE",
            "ingestion-upload:file-001",
            "SECRET"
        );
        when(classificationService.seal(any(CatalogClassificationService.SealCommand.class)))
            .thenReturn(fileSnapshot);

        mockMvc.perform(multipart("/api/ingestion/files/upload-and-parse")
                .file(file)
                .param("classification", "SECRET"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.classification").value("SECRET"))
            .andExpect(jsonPath("$.data.classificationSeal.subjectType").value("FILE"))
            .andExpect(jsonPath("$.data.classificationSeal.subjectKey").value("ingestion-upload:file-001"))
            .andExpect(jsonPath("$.data.classificationSeal.fileFloor").value("SECRET"))
            .andExpect(jsonPath("$.data.fieldClassifications.name").value("SECRET"))
            .andExpect(jsonPath("$.data.fieldClassifications.identity_no").value("SECRET"));

        ArgumentCaptor<CatalogClassificationService.SealCommand> command =
            ArgumentCaptor.forClass(CatalogClassificationService.SealCommand.class);
        verify(classificationService).seal(command.capture());
        assertThat(command.getValue().subjectType()).isEqualTo("FILE");
        assertThat(command.getValue().subjectKey()).isEqualTo("ingestion-upload:file-001");
        assertThat(command.getValue().declaredLevel()).isEqualTo("SECRET");
        assertThat(command.getValue().evidenceChecksum()).isEqualTo(upstreamData.get("fileHash"));
        verify(auditService).auditActionStrict(
            eq("INGESTION_FILE_CLASSIFICATION_SEAL"),
            eq(AuditStage.SUCCESS),
            any(String.class),
            argThat(this::isSafeClassificationAuditPayload)
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void admitTaskRevalidatesStoredFileSealAndActivatesTask() throws Exception {
        CatalogClassificationSnapshot fileSnapshot = snapshot(
            UUID.randomUUID(),
            "FILE",
            "ingestion-upload:file-001",
            "SECRET"
        );
        CatalogClassificationSnapshot taskSnapshot = snapshot(
            UUID.randomUUID(),
            "ASSET",
            "ingestion-file:sealed-task",
            "CONFIDENTIAL"
        );
        Map<String, Object> fileSeal = new java.util.LinkedHashMap<>();
        fileSeal.put("sealId", fileSnapshot.getId());
        fileSeal.put("subjectType", "FILE");
        fileSeal.put("subjectKey", fileSnapshot.getSubjectKey());
        fileSeal.put("snapshotVersion", fileSnapshot.getRecordVersion());
        fileSeal.put("checksum", fileSnapshot.getEvidenceChecksum());
        fileSeal.put("fileFloor", "SECRET");
        Map<String, Object> task = new java.util.LinkedHashMap<>();
        task.put("id", 7);
        task.put("name", "file-draft");
        task.put("status", "draft");
        task.put("sourceType", "csvreader");
        task.put(
            "sourceConfig",
            Map.of(
                "classificationSeal",
                fileSeal,
                "_fileId",
                "file-001",
                "_fileHash",
                fileSnapshot.getEvidenceChecksum(),
                "_encrypted",
                true,
                "fieldClassifications",
                Map.of("identity_no", "CONFIDENTIAL"),
                "_fileColumns",
                java.util.List.of(Map.of("name", "name"), Map.of("name", "identity_no"))
            )
        );
        task.put("fieldClassifications", Map.of("identity_no", "CONFIDENTIAL"));
        when(ingestionClient.getTask(7L)).thenReturn(new ApiResponse<>(200, "ok", task));
        when(classificationService.resolve("FILE", fileSnapshot.getSubjectKey()))
            .thenReturn(java.util.Optional.of(fileSnapshot));
        when(classificationService.sealOrRaise(any(CatalogClassificationService.SealCommand.class)))
            .thenReturn(taskSnapshot);
        when(ingestionClient.admitTask(any(Long.class), anyMap()))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("id", 7, "status", "active")));

        mockMvc.perform(post("/api/ingestion/tasks/7/admit"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(7))
            .andExpect(jsonPath("$.data.status").value("active"));

        ArgumentCaptor<Map<String, Object>> admission = ArgumentCaptor.forClass(Map.class);
        verify(ingestionClient).admitTask(org.mockito.ArgumentMatchers.eq(7L), admission.capture());
        assertThat((Map<String, Object>) admission.getValue().get("classificationSeal"))
            .containsEntry("subjectType", "ASSET")
            .containsEntry("effectiveLevel", "CONFIDENTIAL")
            .containsEntry("fileFloor", "SECRET")
            .containsEntry("fileChecksum", fileSnapshot.getEvidenceChecksum());
        assertThat((Map<String, String>) admission.getValue().get("fieldClassifications"))
            .containsExactlyInAnyOrderEntriesOf(Map.of("name", "SECRET", "identity_no", "CONFIDENTIAL"));
        verify(auditService).auditActionStrict(
            eq("INGESTION_TASK_ADMIT"),
            eq(AuditStage.SUCCESS),
            eq("7"),
            argThat(this::isSafeClassificationAuditPayload)
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void admitTaskReplacesStoredNonFileSealWithCurrentManagedSourceSeal() throws Exception {
        String currentSourceId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        configureClassifiedSource(currentSourceId);
        Map<String, Object> task = new java.util.LinkedHashMap<>();
        task.put("id", 8);
        task.put("sourceType", "mysqlreader");
        task.put("sourceDataSourceId", currentSourceId);
        task.put(
            "classificationSeal",
            Map.of(
                "sealId", "11111111-2222-3333-4444-555555555555",
                "subjectType", "ASSET",
                "subjectKey", "data-source:11111111-2222-3333-4444-555555555555",
                "snapshotVersion", 1,
                "checksum", "old"
            )
        );
        task.put("fieldClassifications", Map.of("legacy_field", "PUBLIC"));
        when(ingestionClient.getTask(8L)).thenReturn(new ApiResponse<>(200, "ok", task));
        when(ingestionClient.admitTask(org.mockito.ArgumentMatchers.eq(8L), anyMap()))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("id", 8, "status", "active")));

        mockMvc.perform(post("/api/ingestion/tasks/8/admit"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("active"));

        ArgumentCaptor<Map<String, Object>> admission = ArgumentCaptor.forClass(Map.class);
        verify(ingestionClient).admitTask(org.mockito.ArgumentMatchers.eq(8L), admission.capture());
        assertThat((Map<String, Object>) admission.getValue().get("classificationSeal"))
            .containsEntry("subjectKey", "data-source:" + currentSourceId);
        assertThat((Map<String, String>) admission.getValue().get("fieldClassifications"))
            .containsExactly(Map.entry("identity_no", "SECRET"));
    }

    @Test
    void admitTaskAllowsManagedDatasourceWithoutClassificationEvidence() throws Exception {
        String sourceId = "33333333-4444-5555-6666-777777777777";
        configureUnclassifiedSource(sourceId);
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", 9);
        task.put("name", "plain-draft");
        task.put("status", "draft");
        task.put("sourceType", "mysqlreader");
        task.put("sourceDataSourceId", sourceId);
        when(ingestionClient.getTask(9L)).thenReturn(new ApiResponse<>(200, "ok", task));
        when(ingestionClient.admitTask(org.mockito.ArgumentMatchers.eq(9L), anyMap()))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("id", 9, "status", "active")));

        mockMvc.perform(post("/api/ingestion/tasks/9/admit"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("active"));

        ArgumentCaptor<Map<String, Object>> admission = ArgumentCaptor.forClass(Map.class);
        verify(ingestionClient).admitTask(org.mockito.ArgumentMatchers.eq(9L), admission.capture());
        assertThat(admission.getValue()).isEmpty();
        verify(classificationService, never()).sealOrRaise(any(CatalogClassificationService.SealCommand.class));
    }

    @Test
    void admitTaskDiscardsLegacyEvidenceWhenTheManagedSourceIsNowUnclassified() throws Exception {
        String sourceId = "55555555-6666-7777-8888-999999999999";
        configureUnclassifiedSource(sourceId);
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", 10);
        task.put("name", "legacy-classified-draft");
        task.put("status", "draft");
        task.put("sourceType", "mysqlreader");
        task.put("sourceDataSourceId", sourceId);
        task.put("classificationSeal", Map.of("sealId", "legacy-seal"));
        task.put("fieldClassifications", Map.of("customer_id", "SECRET"));
        when(ingestionClient.getTask(10L)).thenReturn(new ApiResponse<>(200, "ok", task));
        when(ingestionClient.admitTask(org.mockito.ArgumentMatchers.eq(10L), anyMap()))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("id", 10, "status", "active")));

        mockMvc.perform(post("/api/ingestion/tasks/10/admit"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("active"));

        ArgumentCaptor<Map<String, Object>> admission = ArgumentCaptor.forClass(Map.class);
        verify(ingestionClient).admitTask(org.mockito.ArgumentMatchers.eq(10L), admission.capture());
        assertThat(admission.getValue()).isEmpty();
    }

    private boolean isSafeTaskAuditPayload(Object value) {
        if (!(value instanceof Map<?, ?> payload)) {
            return false;
        }
        return payload.containsKey("auditOperationId") &&
            payload.containsKey("operator") &&
            payload.keySet().stream().noneMatch(key ->
                List.of("password", "sourceConfig", "destinationConfig", "config", "payload")
                    .contains(String.valueOf(key))
            );
    }

    private boolean isSafeClassificationAuditPayload(Object value) {
        if (!(value instanceof Map<?, ?> payload)) {
            return false;
        }
        return isSafeTaskAuditPayload(value) &&
            payload.containsKey("sealId") &&
            payload.containsKey("effectiveLevel") &&
            payload.containsKey("snapshotVersion") &&
            payload.containsKey("evidenceChecksum") &&
            payload.keySet().stream().noneMatch(key ->
                List.of("fieldClassifications", "classificationSeal", "filePath", "originalName")
                    .contains(String.valueOf(key))
            );
    }

    private void configureClassifiedSource(String sourceId) {
        InfraDataSource source = new InfraDataSource();
        source.setId(java.util.UUID.fromString(sourceId));
        source.setName("classified-source");
        source.setProps(
            "{\"classification\":\"INTERNAL\",\"columnClassifications\":{\"identity_no\":\"SECRET\"}}"
        );
        when(dataSourceRepository.findById(source.getId())).thenReturn(java.util.Optional.of(source));

        CatalogClassificationSnapshot snapshot = new CatalogClassificationSnapshot();
        snapshot.setId(java.util.UUID.randomUUID());
        snapshot.setSubjectType("ASSET");
        snapshot.setSubjectKey("data-source:" + sourceId);
        snapshot.setEffectiveLevel("SECRET");
        snapshot.setRecordVersion(1L);
        snapshot.setEvidenceChecksum("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        snapshot.setSealedAt(java.time.Instant.parse("2026-07-26T00:00:00Z"));
        snapshot.setPropagationStatus("SEALED");
        when(classificationService.sealOrRaise(any(CatalogClassificationService.SealCommand.class)))
            .thenReturn(snapshot);
    }

    private void configureUnclassifiedSource(String sourceId) {
        InfraDataSource source = new InfraDataSource();
        source.setId(UUID.fromString(sourceId));
        source.setName("unclassified-source");
        source.setProps("{}");
        when(dataSourceRepository.findById(source.getId())).thenReturn(Optional.of(source));
    }

    private CatalogClassificationSnapshot snapshot(
        UUID id,
        String subjectType,
        String subjectKey,
        String effectiveLevel
    ) {
        CatalogClassificationSnapshot snapshot = new CatalogClassificationSnapshot();
        snapshot.setId(id);
        snapshot.setSubjectType(subjectType);
        snapshot.setSubjectKey(subjectKey);
        snapshot.setAssetType("DATASET");
        snapshot.setEffectiveLevel(effectiveLevel);
        snapshot.setRecordVersion(1L);
        snapshot.setEvidenceChecksum("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        snapshot.setSealedAt(java.time.Instant.parse("2026-07-26T00:00:00Z"));
        snapshot.setPropagationStatus("SEALED");
        return snapshot;
    }
}
