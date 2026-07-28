package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationService;
import com.yuzhi.dts.platform.service.infra.DefaultDestinationSyncService;
import com.yuzhi.dts.platform.service.infra.DefaultDestinationSyncService.DefaultDestinationSnapshot;
import com.yuzhi.dts.platform.service.etl.OdsTableMappingSyncService;
import com.yuzhi.dts.platform.service.ingestion.IngestionClassificationAdmissionService;
import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.service.ops.ExternalRunLogService;
import com.yuzhi.dts.platform.security.session.PortalSessionInactivityFilter;
import com.yuzhi.dts.platform.web.filter.AuditLoggingFilter;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
    private PortalSessionInactivityFilter portalSessionInactivityFilter;

    @MockBean
    private AuditLoggingFilter auditLoggingFilter;

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
                    "\"},\"destination\":{\"config\":{\"table\":[\"t1\"]}}}"
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
        Map<String, Object> seal = (Map<String, Object>) payload.get("classificationSeal");
        assertThat(seal.get("effectiveLevel")).isEqualTo("SECRET");
        assertThat((Map<String, String>) payload.get("fieldClassifications"))
            .containsEntry("identity_no", "SECRET");
        ArgumentCaptor<CatalogClassificationService.SealCommand> command =
            ArgumentCaptor.forClass(CatalogClassificationService.SealCommand.class);
        verify(classificationService).sealOrRaise(command.capture());
        assertThat(command.getValue().declaredLevel()).isEqualTo("INTERNAL");
        assertThat(command.getValue().upstreamLevels().stream().map(String::valueOf).toList())
            .containsExactly("SECRET");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createTaskUsesSelectedTargetDataSourceWhenProvided() throws Exception {
        String targetDataSourceId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        String sourceDataSourceId = "11111111-2222-3333-4444-555555555555";
        configureClassifiedSource(sourceDataSourceId);
        DefaultDestinationSnapshot snapshot = new DefaultDestinationSnapshot(
            "postgresqlwriter",
            "经营分析湖仓",
            Map.of("jdbcUrl", "jdbc:postgresql://analytics-pg:5432/ads", "username", "biadmin"),
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
                          "targetDataSourceId":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
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
        assertThat(config.get("jdbcUrl")).isEqualTo("jdbc:postgresql://analytics-pg:5432/ads");
        assertThat(config.get("username")).isEqualTo("biadmin");
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
    void providedOdsSealIsRevalidatedAndRaisedToTheHighestFieldBeforeForwarding() throws Exception {
        DefaultDestinationSnapshot destination = new DefaultDestinationSnapshot(
            "rdbmswriter",
            "lake",
            Map.of("connection", java.util.List.of(Map.of("jdbcUrl", java.util.List.of("jdbc:pg")))),
            "11111111-2222-3333-4444-555555555555"
        );
        when(destinationSyncService.ensureDefaultDestination()).thenReturn(destination);
        when(ingestionClient.createIngestionTask(anyMap()))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("task", "ods-demo")));

        CatalogClassificationSnapshot sourceSnapshot = snapshot(
            UUID.randomUUID(),
            "ASSET",
            "data-source:aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
            "INTERNAL"
        );
        CatalogClassificationSnapshot taskSnapshot = snapshot(
            UUID.randomUUID(),
            "ASSET",
            "ingestion-seal:asset:" + sourceSnapshot.getSubjectKey(),
            "SECRET"
        );
        when(classificationService.resolve("ASSET", sourceSnapshot.getSubjectKey()))
            .thenReturn(java.util.Optional.of(sourceSnapshot));
        when(classificationService.sealOrRaise(any(CatalogClassificationService.SealCommand.class)))
            .thenReturn(taskSnapshot);

        mockMvc.perform(post("/api/ingestion/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name":"ods-task",
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
                        sourceSnapshot.getId(),
                        sourceSnapshot.getSubjectKey(),
                        sourceSnapshot.getEvidenceChecksum()
                    )
                ))
            .andExpect(status().isOk());

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(ingestionClient).createIngestionTask(payload.capture());
        assertThat((Map<String, Object>) payload.getValue().get("classificationSeal"))
            .containsEntry("effectiveLevel", "SECRET")
            .containsEntry("subjectKey", taskSnapshot.getSubjectKey());
        ArgumentCaptor<CatalogClassificationService.SealCommand> command =
            ArgumentCaptor.forClass(CatalogClassificationService.SealCommand.class);
        verify(classificationService).sealOrRaise(command.capture());
        assertThat(command.getValue().upstreamLevels().stream().map(String::valueOf).toList())
            .containsExactlyInAnyOrder("INTERNAL", "SECRET");
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
