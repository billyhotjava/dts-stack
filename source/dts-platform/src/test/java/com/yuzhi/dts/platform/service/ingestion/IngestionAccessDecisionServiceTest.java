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
    @SuppressWarnings("unchecked")
    void browserFileResponseRemovesHostAndContainerPathsRecursively() {
        Object sanitized = service.removeInternalFilePaths(Map.of(
            "fileId", "file-1",
            "hostPath", "/srv/plain.xlsx",
            "classificationSeal", Map.of("effectiveLevel", "SECRET", "fileFloor", "SECRET"),
            "nested", Map.of(
                "containerPath", "/decrypted/plain.xlsx",
                "_filePath", "/tmp/plain.xlsx",
                "_containerPath", "/decrypted/plain.xlsx",
                "filePath", "/srv/plain.xlsx",
                "path", "/decrypted/plain.xlsx",
                "fileHash", "abc"
            ),
            "apiResource", Map.of("resourceId", "customers", "path", "/tmp/customers")
        ));

        Map<String, Object> result = (Map<String, Object>) sanitized;
        assertThat(result)
            .doesNotContainKeys("hostPath")
            .containsEntry("fileId", "file-1")
            .containsKey("classificationSeal");
        assertThat((Map<String, Object>) result.get("nested"))
            .doesNotContainKeys("containerPath", "_filePath", "_containerPath", "filePath", "path")
            .containsEntry("fileHash", "abc");
        assertThat((Map<String, Object>) result.get("apiResource"))
            .containsEntry("path", "/tmp/customers");
    }

    private Map<String, Object> databaseTask(long id, UUID sourceId, UUID targetId) {
        return Map.of(
            "id", id,
            "sourceType", "mysqlreader",
            "sourceDataSourceId", sourceId.toString(),
            "destinationConfig", Map.of("targetDataSourceId", targetId.toString())
        );
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
