package com.yuzhi.dts.platform.service.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.ClassificationUtils;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class IngestionAccessDecisionServiceTest {

    @Mock
    private InfraDataSourceRepository dataSourceRepository;

    @Mock
    private IngestionServiceClient ingestionClient;

    @Mock
    private ClassificationUtils classificationUtils;

    private IngestionAccessDecisionService service;

    @BeforeEach
    void setUp() {
        service = new IngestionAccessDecisionService(
            dataSourceRepository,
            ingestionClient,
            classificationUtils,
            new ObjectMapper()
        );
        authenticateDepartmentOwner("dept-a");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void taskAccessRejectsCrossDepartmentSourceEvenWhenTargetIsVisible() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(ingestionClient.getTask(7L)).thenReturn(new ApiResponse<>(200, "ok", databaseTask(7L, sourceId, targetId)));
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(dataSource(sourceId, "MYSQL", "dept-b", "INTERNAL")));

        assertForbidden(() -> service.requireTaskAccess(7L, false));
        verify(dataSourceRepository, never()).findById(targetId);
    }

    @Test
    void databaseMutationRequiresExplicitClearanceAndRejectsHigherSourceLevel() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(dataSource(sourceId, "MYSQL", "dept-a", "SECRET")));
        when(classificationUtils.getCurrentUserExplicitMaxLevel()).thenReturn(Optional.empty());

        assertForbidden(() -> service.requireCreateOrUpdateAccess(databaseTask(7L, sourceId, targetId), true));

        when(classificationUtils.getCurrentUserExplicitMaxLevel()).thenReturn(Optional.of("INTERNAL"));
        assertForbidden(() -> service.requireCreateOrUpdateAccess(databaseTask(7L, sourceId, targetId), true));
        verify(dataSourceRepository, never()).findById(targetId);
    }

    @Test
    void conflictingTopLevelAndNestedSourceIdsFailBeforeAuthorization() {
        UUID ownSourceId = UUID.randomUUID();
        UUID foreignSourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("sourceType", "mysqlreader");
        task.put("sourceDataSourceId", ownSourceId.toString());
        task.put("source", Map.of("dataSourceId", foreignSourceId.toString()));
        task.put("destinationConfig", Map.of("targetDataSourceId", targetId.toString()));
        when(ingestionClient.getTask(8L)).thenReturn(new ApiResponse<>(200, "ok", task));

        assertBadRequest(() -> service.requireTaskAccess(8L, true));

        verify(dataSourceRepository, never()).findById(ownSourceId);
        verify(dataSourceRepository, never()).findById(foreignSourceId);
        verify(dataSourceRepository, never()).findById(targetId);
    }

    @Test
    void conflictingTopLevelAndNestedTargetIdsFailBeforeAuthorization() {
        UUID sourceId = UUID.randomUUID();
        UUID ownTargetId = UUID.randomUUID();
        UUID foreignTargetId = UUID.randomUUID();
        Map<String, Object> task = Map.of(
            "sourceType", "mysqlreader",
            "sourceDataSourceId", sourceId.toString(),
            "targetDataSourceId", ownTargetId.toString(),
            "destination", Map.of("config", Map.of("targetDataSourceId", foreignTargetId.toString()))
        );

        assertBadRequest(() -> service.canonicalizeTaskPayloadIdentifiers(task));

        verify(dataSourceRepository, never()).findById(sourceId);
        verify(dataSourceRepository, never()).findById(ownTargetId);
        verify(dataSourceRepository, never()).findById(foreignTargetId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void matchingAliasesAreWrittenBackAsOneCanonicalIdentifier() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Map<String, Object> canonical = service.canonicalizeTaskPayloadIdentifiers(Map.of(
            "sourceDataSourceId", sourceId.toString(),
            "source", Map.of("config", Map.of("dataSourceId", sourceId.toString())),
            "destinationConfig", Map.of("destinationDataSourceId", targetId.toString())
        ));

        assertThat(canonical)
            .containsEntry("sourceDataSourceId", sourceId.toString())
            .containsEntry("targetDataSourceId", targetId.toString());
        assertThat((Map<String, Object>) ((Map<String, Object>) canonical.get("source")).get("config"))
            .containsEntry("dataSourceId", sourceId.toString());
        assertThat((Map<String, Object>) canonical.get("destinationConfig"))
            .containsEntry("targetDataSourceId", targetId.toString())
            .containsEntry("destinationDataSourceId", targetId.toString());
    }

    @Test
    void fileTaskRequiresExplicitClearanceAndVisibleActiveTarget() {
        UUID targetId = UUID.randomUUID();
        when(dataSourceRepository.findById(targetId)).thenReturn(Optional.of(dataSource(targetId, "POSTGRESQL", "dept-a", null)));
        when(classificationUtils.getCurrentUserExplicitMaxLevel()).thenReturn(Optional.of("INTERNAL"));
        Map<String, Object> task = Map.of(
            "sourceType", "excelreader",
            "sourceConfig", Map.of(
                "_fileId", "file-1",
                "classificationSeal", Map.of("effectiveLevel", "INTERNAL", "fileFloor", "INTERNAL")
            ),
            "destinationConfig", Map.of("targetDataSourceId", targetId.toString())
        );

        service.requireCreateOrUpdateAccess(task, true);

        verify(classificationUtils).getCurrentUserExplicitMaxLevel();
    }

    @Test
    void sameDepartmentUserCannotReuseFileTaskAboveExplicitClearanceEvenOnReadPath() {
        UUID targetId = UUID.randomUUID();
        Map<String, Object> task = Map.of(
            "id", 9,
            "sourceType", "excelreader",
            "sourceConfig", Map.of(
                "_fileId", "file-1",
                "classificationSeal", Map.of("effectiveLevel", "SECRET", "fileFloor", "SECRET")
            ),
            "destinationConfig", Map.of("targetDataSourceId", targetId.toString())
        );
        when(ingestionClient.getTask(9L)).thenReturn(new ApiResponse<>(200, "ok", task));
        when(classificationUtils.getCurrentUserExplicitMaxLevel()).thenReturn(Optional.of("INTERNAL"));

        assertForbidden(() -> service.requireTaskAccess(9L, false));

        verify(dataSourceRepository, never()).findById(targetId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fileTaskWithManagedSourceRequiresSourceVisibilityAndClearanceForDetailAndList() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Map<String, Object> task = new LinkedHashMap<>(fileTask(24L, targetId, "INTERNAL"));
        task.put("sourceDataSourceId", sourceId.toString());
        when(classificationUtils.getCurrentUserExplicitMaxLevel()).thenReturn(Optional.of("INTERNAL"));
        when(ingestionClient.getTask(24L)).thenReturn(new ApiResponse<>(200, "ok", task));
        when(dataSourceRepository.findById(sourceId))
            .thenReturn(Optional.of(dataSource(sourceId, "FILE", "dept-a", "SECRET")));

        assertForbidden(() -> service.requireTaskAccess(24L, false));
        verify(dataSourceRepository, never()).findById(targetId);

        when(dataSourceRepository.findAll()).thenReturn(List.of(
            dataSource(sourceId, "FILE", "dept-a", "SECRET"),
            dataSource(targetId, "POSTGRESQL", "dept-a", null)
        ));
        when(ingestionClient.listTasks(Map.of("page", 0, "size", 500)))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("content", List.of(task), "totalElements", 1)));

        ApiResponse<Map<String, Object>> response = service.listVisibleTasks(Map.of("page", "0", "size", "20"));

        assertThat(response.getData()).containsEntry("totalElements", 0);
        assertThat((List<Map<String, Object>>) response.getData().get("content")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void departmentTaskListIsFilteredBeforeAccurateRepagination() {
        UUID ownSource = UUID.randomUUID();
        UUID ownTarget = UUID.randomUUID();
        UUID foreignSource = UUID.randomUUID();
        UUID foreignTarget = UUID.randomUUID();
        when(dataSourceRepository.findAll()).thenReturn(List.of(
            dataSource(ownSource, "MYSQL", "dept-a", "INTERNAL"),
            dataSource(ownTarget, "POSTGRESQL", "dept-a", null),
            dataSource(foreignSource, "MYSQL", "dept-b", "INTERNAL"),
            dataSource(foreignTarget, "POSTGRESQL", "dept-b", null)
        ));
        when(classificationUtils.getCurrentUserExplicitMaxLevel()).thenReturn(Optional.of("INTERNAL"));
        when(ingestionClient.listTasks(Map.of("page", 0, "size", 500)))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of(
                "content", List.of(
                    databaseTask(1L, ownSource, ownTarget),
                    databaseTask(2L, foreignSource, foreignTarget)
                ),
                "totalElements", 2
            )));

        ApiResponse<Map<String, Object>> response = service.listVisibleTasks(Map.of("page", "0", "size", "1"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("totalElements", 1).containsEntry("totalPages", 1);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getData().get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0)).containsEntry("id", 1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void instituteTaskListStillFiltersFileTasksAboveExplicitClearanceAndRecomputesTotals() {
        authenticateInstituteOwner();
        UUID allowedSourceId = UUID.randomUUID();
        UUID blockedSourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(classificationUtils.getCurrentUserExplicitMaxLevel()).thenReturn(Optional.of("INTERNAL"));
        when(dataSourceRepository.findAll()).thenReturn(List.of(
            dataSource(allowedSourceId, "FILE", "dept-a", "INTERNAL"),
            dataSource(blockedSourceId, "FILE", "dept-a", "SECRET"),
            dataSource(targetId, "POSTGRESQL", "dept-a", null)
        ));
        when(ingestionClient.listTasks(Map.of("page", 0, "size", 500)))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of(
                "content", List.of(
                    fileTask(11L, allowedSourceId, targetId, "INTERNAL"),
                    fileTask(12L, blockedSourceId, targetId, "SECRET")
                ),
                "totalElements", 2
            )));

        ApiResponse<Map<String, Object>> response = service.listVisibleTasks(Map.of("page", "0", "size", "20"));

        assertThat(response.getData()).containsEntry("totalElements", 1).containsEntry("totalPages", 1);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getData().get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0)).containsEntry("id", 11L);
        verify(dataSourceRepository).findAll();
    }

    @Test
    void databaseAndApiTaskReadsRequireSourceClearance() {
        UUID databaseSource = UUID.randomUUID();
        UUID apiSource = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(classificationUtils.getCurrentUserExplicitMaxLevel()).thenReturn(Optional.of("INTERNAL"));
        when(dataSourceRepository.findById(databaseSource))
            .thenReturn(Optional.of(dataSource(databaseSource, "MYSQL", "dept-a", "SECRET")));
        when(dataSourceRepository.findById(apiSource))
            .thenReturn(Optional.of(dataSource(apiSource, "API", "dept-a", "SECRET")));
        when(ingestionClient.getTask(21L))
            .thenReturn(new ApiResponse<>(200, "ok", databaseTask(21L, databaseSource, targetId)));
        when(ingestionClient.getTask(22L))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of(
                "id", 22L,
                "sourceType", "httpreader",
                "sourceDataSourceId", apiSource.toString(),
                "destinationConfig", Map.of("targetDataSourceId", targetId.toString())
            )));

        assertForbidden(() -> service.requireTaskAccess(21L, false));
        assertForbidden(() -> service.requireTaskAccess(22L, false));

        verify(dataSourceRepository, never()).findById(targetId);
    }

    @Test
    void updateAuthorizationUsesMinimalTaskMetadataProjection() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Map<String, Object> metadata = databaseTask(25L, sourceId, targetId);
        when(ingestionClient.getTaskAccessMetadata(25L)).thenReturn(new ApiResponse<>(200, "ok", metadata));
        when(dataSourceRepository.findById(sourceId))
            .thenReturn(Optional.of(dataSource(sourceId, "MYSQL", "dept-a", "INTERNAL")));
        when(dataSourceRepository.findById(targetId))
            .thenReturn(Optional.of(dataSource(targetId, "POSTGRESQL", "dept-a", null)));
        when(classificationUtils.getCurrentUserExplicitMaxLevel()).thenReturn(Optional.of("INTERNAL"));

        assertThat(service.requireTaskAuthorizationAccess(25L, true))
            .containsEntry("sourceDataSourceId", sourceId.toString())
            .containsEntry("targetDataSourceId", targetId.toString());

        verify(ingestionClient, never()).getTask(25L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ownerlessDataSourceIsDeniedForDepartmentDetailAndList() {
        UUID ownerlessSource = UUID.randomUUID();
        UUID ownTarget = UUID.randomUUID();
        Map<String, Object> task = databaseTask(23L, ownerlessSource, ownTarget);
        when(ingestionClient.getTask(23L)).thenReturn(new ApiResponse<>(200, "ok", task));
        when(dataSourceRepository.findById(ownerlessSource))
            .thenReturn(Optional.of(dataSource(ownerlessSource, "MYSQL", null, "INTERNAL")));

        assertForbidden(() -> service.requireTaskAccess(23L, false));

        when(classificationUtils.getCurrentUserExplicitMaxLevel()).thenReturn(Optional.of("SECRET"));
        when(dataSourceRepository.findAll()).thenReturn(List.of(
            dataSource(ownerlessSource, "MYSQL", null, "INTERNAL"),
            dataSource(ownTarget, "POSTGRESQL", "dept-a", null)
        ));
        when(ingestionClient.listTasks(Map.of("page", 0, "size", 500)))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("content", List.of(task), "totalElements", 1)));

        ApiResponse<Map<String, Object>> response = service.listVisibleTasks(Map.of("page", "0", "size", "20"));

        assertThat(response.getData()).containsEntry("totalElements", 0).containsEntry("totalPages", 0);
        assertThat((List<Map<String, Object>>) response.getData().get("content")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void taskListFiltersDatabaseAndApiSourcesAboveExplicitClearance() {
        UUID readableSource = UUID.randomUUID();
        UUID secretDatabaseSource = UUID.randomUUID();
        UUID secretApiSource = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(classificationUtils.getCurrentUserExplicitMaxLevel()).thenReturn(Optional.of("INTERNAL"));
        when(dataSourceRepository.findAll()).thenReturn(List.of(
            dataSource(readableSource, "MYSQL", "dept-a", "INTERNAL"),
            dataSource(secretDatabaseSource, "MYSQL", "dept-a", "SECRET"),
            dataSource(secretApiSource, "API", "dept-a", "SECRET"),
            dataSource(targetId, "POSTGRESQL", "dept-a", null)
        ));
        Map<String, Object> apiTask = Map.of(
            "id", 32L,
            "sourceType", "httpreader",
            "sourceDataSourceId", secretApiSource.toString(),
            "destinationConfig", Map.of("targetDataSourceId", targetId.toString())
        );
        when(ingestionClient.listTasks(Map.of("page", 0, "size", 500)))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of(
                "content", List.of(
                    databaseTask(30L, readableSource, targetId),
                    databaseTask(31L, secretDatabaseSource, targetId),
                    apiTask
                ),
                "totalElements", 3
            )));

        ApiResponse<Map<String, Object>> response = service.listVisibleTasks(Map.of("page", "0", "size", "20"));

        assertThat(response.getData()).containsEntry("totalElements", 1);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getData().get("content");
        assertThat(content).extracting(task -> task.get("id")).containsExactly(30L);
    }

    @Test
    void apiConnectionTestRejectsNetworkOverridesAndAbsoluteMetadataPaths() {
        UUID sourceId = UUID.randomUUID();
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(dataSource(sourceId, "API", "dept-a", "INTERNAL")));
        when(classificationUtils.getCurrentUserExplicitMaxLevel()).thenReturn(Optional.of("SECRET"));

        assertBadRequest(() -> service.normalizeApiConnectionTest(Map.of(
                "dataSourceId", sourceId.toString(),
                "baseUrl", "http://169.254.169.254/latest/meta-data"
            )));
        assertBadRequest(() -> service.normalizeApiConnectionTest(Map.of(
                "dataSourceId", sourceId.toString(),
                "resource", Map.of("path", "http://169.254.169.254/latest/meta-data")
            )));
        assertBadRequest(() -> service.normalizeApiConnectionTest(Map.of(
                "dataSourceId", sourceId.toString(),
                "resource", Map.of("path", "/health", "allowedHosts", List.of("localhost"))
            )));
    }

    @Test
    void dataSourceScopedRollbackAuditRequiresExplicitClearanceEvenWhenTaskScopeIsAlsoPresent() {
        UUID dataSourceId = UUID.randomUUID();
        when(dataSourceRepository.findById(dataSourceId))
            .thenReturn(Optional.of(dataSource(dataSourceId, "MYSQL", "dept-a", "SECRET")));
        when(classificationUtils.getCurrentUserExplicitMaxLevel()).thenReturn(Optional.of("INTERNAL"));

        assertForbidden(() -> service.requireRollbackAuditAccess(7L, dataSourceId));
        verify(ingestionClient, never()).getTaskAccessMetadata(7L);
    }

    @Test
    void dataSourceScopedRollbackAuditRejectsMissingClassificationAndMissingScopeFailsClosed() {
        UUID dataSourceId = UUID.randomUUID();
        when(dataSourceRepository.findById(dataSourceId))
            .thenReturn(Optional.of(dataSource(dataSourceId, "MYSQL", "dept-a", null)));
        when(classificationUtils.getCurrentUserExplicitMaxLevel()).thenReturn(Optional.of("SECRET"));

        assertForbidden(() -> service.requireRollbackAuditAccess(null, dataSourceId));
        assertForbidden(() -> service.requireRollbackAuditAccess(null, null));
    }

    @Test
    void aggregateDataSourceScopeRejectsBlankInvalidAndUnclearedIdentifiers() {
        assertBadRequest(() -> service.requireAggregateScope(Map.of("sourceDataSourceId", ""), "查看接入聚合"));
        assertBadRequest(() -> service.requireAggregateScope(Map.of("sourceDataSourceId", "not-a-uuid"), "查看接入聚合"));

        UUID dataSourceId = UUID.randomUUID();
        when(dataSourceRepository.findById(dataSourceId))
            .thenReturn(Optional.of(dataSource(dataSourceId, "MYSQL", "dept-a", "SECRET")));
        when(classificationUtils.getCurrentUserExplicitMaxLevel()).thenReturn(Optional.of("INTERNAL"));

        assertForbidden(() ->
            service.requireAggregateScope(Map.of("sourceDataSourceId", dataSourceId.toString()), "查看接入聚合")
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void browserFileResponseRemovesHostAndContainerPathsRecursively() {
        Object sanitized = service.removeInternalFilePaths(Map.of(
            "fileId", "file-1",
            "hostPath", "/srv/plain.xlsx",
            "host_path", List.of("/srv/plain.xlsx"),
            "addaxJobPath", new String[] { "/opt/airflow/jobs/task.json" },
            "classificationSeal", Map.of("effectiveLevel", "SECRET", "fileFloor", "SECRET"),
            "nested", Map.of(
                "containerPath", "/decrypted/plain.xlsx",
                "_filePath", "/tmp/plain.xlsx",
                "_container_path", new String[] { "/decrypted/plain.xlsx" },
                "filePath", "/srv/plain.xlsx",
                "path", new String[] { "/decrypted/plain.xlsx", "/tmp/plain.xlsx" },
                "fileHash", "abc"
            ),
            "resource", Map.of("resourceId", "customers", "path", "/tmp/customers"),
            "resources", List.of(Map.of("resourceId", "orders", "path", "/v1/orders"))
        ));

        Map<String, Object> result = (Map<String, Object>) sanitized;
        assertThat(result)
            .doesNotContainKeys("hostPath", "host_path", "addaxJobPath")
            .containsEntry("fileId", "file-1")
            .containsKey("classificationSeal");
        assertThat((Map<String, Object>) result.get("nested"))
            .doesNotContainKeys("containerPath", "_filePath", "_container_path", "filePath", "path")
            .containsEntry("fileHash", "abc");
        assertThat((Map<String, Object>) result.get("resource"))
            .containsEntry("path", "/tmp/customers");
        assertThat((Map<String, Object>) ((List<?>) result.get("resources")).get(0))
            .containsEntry("path", "/v1/orders");

        Object arraySanitized = service.removeInternalFilePaths(new Object[] {
            Map.of("addax_job_path", "/opt/airflow/jobs/task.json", "fileId", "file-2")
        });
        assertThat((Map<String, Object>) ((List<?>) arraySanitized).get(0))
            .doesNotContainKey("addax_job_path")
            .containsEntry("fileId", "file-2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void responseSanitizerRecursivelyRemovesSecretsButPreservesClassificationEvidence() {
        Map<String, Object> leaked = new LinkedHashMap<>();
        leaked.put("id", 7L);
        leaked.put("password", "db-password");
        leaked.put("PASSWD", "legacy-password");
        leaked.put("token", "raw-token");
        leaked.put("accessToken", "access-token");
        leaked.put("refresh_token", "refresh-token");
        leaked.put("secret", "raw-secret");
        leaked.put("client-secret", "client-secret");
        leaked.put("apiKey", "api-key");
        leaked.put("Authorization", "Bearer credential");
        leaked.put("dbPassword", "db-password-alias");
        leaked.put("AUTH_TOKEN", "auth-token");
        leaked.put("pwd", "short-password");
        leaked.put("passphrase", "key-passphrase");
        leaked.put("secretAccessKey", "cloud-secret");
        leaked.put("credential", Map.of("username", "demo", "password", "nested-password"));
        leaked.put("secureProps", Map.of("privateKey", "private-key"));
        leaked.put("items", List.of(Map.of("name", "safe", "CLIENTSECRET", "nested-secret")));
        leaked.put(
            "classificationSeal",
            Map.of("effectiveLevel", "SECRET", "checksum", "seal-checksum", "fileChecksum", "file-checksum")
        );

        ApiResponse<Map<String, Object>> response = service.sanitizeResponse(new ApiResponse<>(200, "ok", leaked));
        Map<String, Object> result = response.getData();

        assertThat(result)
            .containsEntry("id", 7L)
            .doesNotContainKeys(
                "password",
                "PASSWD",
                "token",
                "accessToken",
                "refresh_token",
                "secret",
                "client-secret",
                "apiKey",
                "Authorization",
                "dbPassword",
                "AUTH_TOKEN",
                "pwd",
                "passphrase",
                "secretAccessKey",
                "credential",
                "secureProps"
            );
        assertThat((Map<String, Object>) ((List<?>) result.get("items")).get(0))
            .containsEntry("name", "safe")
            .doesNotContainKey("CLIENTSECRET");
        assertThat((Map<String, Object>) result.get("classificationSeal"))
            .containsEntry("checksum", "seal-checksum")
            .containsEntry("fileChecksum", "file-checksum")
            .containsEntry("effectiveLevel", "SECRET");
    }

    @Test
    void responseSanitizerRedactsStructuredAndMalformedAuditStringsWithoutChangingBusinessText() {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put(
            "requestJson",
            "{\"request\":{\"password\":\"request-secret\",\"hostPath\":\"/opt/rollback/request.json\"},\"scope\":\"task\"}"
        );
        audit.put(
            "impactJson",
            "{\"impact\":[{\"authToken\":\"impact-token\",\"containerPath\":\"/decrypted/impact.json\",\"rows\":3}],\"safe\":\"kept\"}"
        );
        audit.put(
            "resultJson",
            "{\"result\":{\"token\":\"result secret with spaces"
        );
        audit.put(
            "errorMessage",
            "rollback failed password=plain-secret; Authorization: Bearer credential-token; " +
            "authorization=Basic ZHVtbXk6c2VjcmV0; " +
            "hostPath=/opt/rollback/error.log; containerPath=/decrypted/error.log; reason=timeout"
        );
        audit.put("businessMessage", "订单接口 /v1/orders 状态正常，支持 Bearer token 鉴权");

        ApiResponse<Map<String, Object>> response = service.sanitizeResponse(new ApiResponse<>(200, "ok", audit));
        Map<String, Object> result = response.getData();

        assertThat((String) result.get("requestJson"))
            .contains("\"scope\":\"task\"")
            .doesNotContain("request-secret", "/opt/rollback/request.json", "password", "hostPath");
        assertThat((String) result.get("impactJson"))
            .contains("\"rows\":3", "\"safe\":\"kept\"")
            .doesNotContain("impact-token", "/decrypted/impact.json", "authToken", "containerPath");
        assertThat((String) result.get("resultJson"))
            .doesNotContain("result secret with spaces");
        assertThat((String) result.get("errorMessage"))
            .contains("reason=timeout")
            .doesNotContain(
                "plain-secret",
                "credential-token",
                "ZHVtbXk6c2VjcmV0",
                "/opt/rollback/error.log",
                "/decrypted/error.log"
            );
        assertThat(result).containsEntry("businessMessage", "订单接口 /v1/orders 状态正常，支持 Bearer token 鉴权");
    }

    @Test
    @SuppressWarnings("unchecked")
    void mergedDestinationRetainsOnlySecretsExplicitAtTheSameRequestPath() {
        Map<String, Object> merged = Map.of(
            "password", "request-password",
            "clientSecret", "default-client-secret",
            "dbPassword", "request-db-password",
            "authToken", "default-auth-token",
            "jdbcUrl", "jdbc:postgresql://lake/db",
            "connection", List.of(Map.of(
                "password", "default-nested-password",
                "apiKey", "request-api-key",
                "PassPhrase", "request-passphrase",
                "secretAccessKey", "default-cloud-secret",
                "pwd", "default-short-password",
                "table", List.of("ods_demo")
            ))
        );
        Map<String, Object> explicit = Map.of(
            "password", "request-password",
            "DB_PASSWORD", "request-db-password",
            "connection", List.of(Map.of(
                "apiKey", "request-api-key",
                "pass_phrase", "request-passphrase"
            ))
        );

        Map<String, Object> retained = (Map<String, Object>) service.retainExplicitSecrets(merged, explicit);
        Map<String, Object> connection = (Map<String, Object>) ((List<?>) retained.get("connection")).get(0);

        assertThat(retained)
            .containsEntry("password", "request-password")
            .containsEntry("dbPassword", "request-db-password")
            .containsEntry("jdbcUrl", "jdbc:postgresql://lake/db")
            .doesNotContainKeys("clientSecret", "authToken");
        assertThat(connection)
            .containsEntry("apiKey", "request-api-key")
            .containsEntry("PassPhrase", "request-passphrase")
            .containsEntry("table", List.of("ods_demo"))
            .doesNotContainKeys("password", "secretAccessKey", "pwd");
    }

    private Map<String, Object> databaseTask(long id, UUID sourceId, UUID targetId) {
        return Map.of(
            "id", id,
            "sourceType", "mysqlreader",
            "sourceDataSourceId", sourceId.toString(),
            "destinationConfig", Map.of("targetDataSourceId", targetId.toString())
        );
    }

    private Map<String, Object> fileTask(long id, UUID targetId, String level) {
        return Map.of(
            "id", id,
            "sourceType", "excelreader",
            "sourceConfig", Map.of(
                "_fileId", "file-" + id,
                "classificationSeal", Map.of("effectiveLevel", level, "fileFloor", level)
            ),
            "destinationConfig", Map.of("targetDataSourceId", targetId.toString())
        );
    }

    private Map<String, Object> fileTask(long id, UUID sourceId, UUID targetId, String level) {
        Map<String, Object> task = new LinkedHashMap<>(fileTask(id, targetId, level));
        task.put("sourceDataSourceId", sourceId.toString());
        return task;
    }

    private InfraDataSource dataSource(UUID id, String type, String ownerDept, String classification) {
        InfraDataSource source = new InfraDataSource();
        source.setId(id);
        source.setType(type);
        source.setConnectorKey(type == null ? null : type.toLowerCase(java.util.Locale.ROOT));
        source.setOwnerDept(ownerDept);
        source.setStatus("ACTIVE");
        if (classification != null) {
            source.setProps("{\"classification\":\"" + classification + "\"}");
        }
        return source;
    }

    private void authenticateDepartmentOwner(String department) {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "dept-owner")
            .claim("dept_code", department)
            .build();
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority(AuthoritiesConstants.DEPT_DATA_OWNER))
            )
        );
    }

    private void authenticateInstituteOwner() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "institute-owner")
            .claim("realm_access", Map.of("roles", List.of("INST_DATA_OWNER")))
            .build();
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority(AuthoritiesConstants.INST_DATA_OWNER))
            )
        );
    }

    private void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)
            );
    }

    private void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
    }
}
