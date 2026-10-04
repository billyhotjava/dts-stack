package com.yuzhi.dts.platform.web.rest.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.config.PlatformInboundServiceAuthProperties;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.infra.HiveConnectionTestResult;
import com.yuzhi.dts.platform.service.infra.InfraManagementService;
import com.yuzhi.dts.platform.service.infra.JdbcConnectionTestService;
import com.yuzhi.dts.platform.service.infra.dto.DataSourceRequest;
import com.yuzhi.dts.platform.service.infra.dto.InfraDataSourceDto;
import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.service.infra.dto.InfraDataSourceDetailDto;
import com.yuzhi.dts.platform.service.services.SvcTokenAuthService;
import com.yuzhi.dts.platform.service.services.SvcTokenAuthService.TokenPrincipal;
import com.yuzhi.dts.platform.security.policy.PersonnelLevel;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class InfraDataSourceResourceTest {

    @Mock
    private InfraManagementService infraManagementService;

    @Mock
    private AuditService auditService;

    @Mock
    private JdbcConnectionTestService jdbcConnectionTestService;

    @Mock
    private IngestionServiceClient ingestionServiceClient;

    @Mock
    private SvcTokenAuthService svcTokenAuthService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private PlatformInboundServiceAuthProperties properties;
    private InfraDataSourceResource resource;

    @BeforeEach
    void setUp() {
        properties = new PlatformInboundServiceAuthProperties();
        properties.setTrustedServices(Map.of("dts-ingestion", "svc-secret"));
        resource = new InfraDataSourceResource(
            infraManagementService,
            auditService,
            jdbcConnectionTestService,
            ingestionServiceClient,
            properties,
            svcTokenAuthService,
            transactionManager
        );
        lenient().when(auditService.auditActionStrict(anyString(), any(AuditStage.class), anyString(), any()))
            .thenReturn(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void runtimeDetailRejectsUserPrincipalEvenWithServiceToken() {
        UUID id = UUID.randomUUID();
        authenticate("opadmin", AuthoritiesConstants.OP_ADMIN);

        assertThatThrownBy(() -> resource.runtimeDetail(id, "svc-secret"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);

        verify(infraManagementService, never()).getDataSourceRuntimeDetail(id);
    }

    @Test
    void runtimeDetailRejectsServicePrincipalWithoutMatchingServiceToken() {
        UUID id = UUID.randomUUID();
        authenticate("service:dts-ingestion", AuthoritiesConstants.SERVICE_INTERNAL);

        assertThatThrownBy(() -> resource.runtimeDetail(id, "wrong-secret"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);

        verify(infraManagementService, never()).getDataSourceRuntimeDetail(id);
    }

    @Test
    void runtimeDetailAllowsServicePrincipalWithServiceToken() {
        UUID id = UUID.randomUUID();
        authenticate("service:dts-ingestion", AuthoritiesConstants.SERVICE_INTERNAL);
        InfraDataSourceDetailDto detail = new InfraDataSourceDetailDto(
            id,
            "erp-db",
            "postgresql",
            "postgresql",
            "jdbc:postgresql://db:5432/erp",
            "erp_user",
            null,
            null,
            Map.of(),
            Map.of("password", "secret"),
            List.of(),
            "ACTIVE",
            null
        );
        when(infraManagementService.getDataSourceRuntimeDetail(id)).thenReturn(detail);

        InfraDataSourceDetailDto response = resource.runtimeDetail(id, "svc-secret").getData();

        assertThat(response.secrets()).containsEntry("password", "secret");
        verify(infraManagementService).getDataSourceRuntimeDetail(id);
    }

    @Test
    void runtimeDetailAllowsServicePrincipalWithDatabaseManagedServiceToken() {
        UUID id = UUID.randomUUID();
        properties.setTrustedServices(Map.of());
        authenticate("service:dts-ingestion", AuthoritiesConstants.SERVICE_INTERNAL);
        InfraDataSourceDetailDto detail = new InfraDataSourceDetailDto(
            id,
            "erp-db",
            "postgresql",
            "postgresql",
            "jdbc:postgresql://db:5432/erp",
            "erp_user",
            null,
            null,
            Map.of(),
            Map.of("password", "secret"),
            List.of(),
            "ACTIVE",
            null
        );
        when(svcTokenAuthService.authenticateService("db-managed-secret", "dts-ingestion"))
            .thenReturn(new TokenPrincipal("service:dts-ingestion", null, PersonnelLevel.GENERAL, null));
        when(infraManagementService.getDataSourceRuntimeDetail(id)).thenReturn(detail);

        InfraDataSourceDetailDto response = resource.runtimeDetail(id, "db-managed-secret").getData();

        assertThat(response.secrets()).containsEntry("password", "secret");
        verify(infraManagementService).getDataSourceRuntimeDetail(id);
    }

    @Test
    void testApiDataSourceUsesIngestionEngineAndMarksVerified() {
        UUID id = UUID.randomUUID();
        when(infraManagementService.getDataSource(id, null)).thenReturn(new InfraDataSourceDto(id, "CRM API", "api", null, null));
        when(ingestionServiceClient.testApiConnection(Map.of("dataSourceId", id.toString())))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("connected", true, "httpStatus", 200, "sampleCount", 2, "elapsedMs", 42)));

        HiveConnectionTestResult result = resource.test(id, null).getData();

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("HTTP 200").contains("样本 2 条");
        assertThat(result.elapsedMillis()).isEqualTo(42);
        verify(ingestionServiceClient).testApiConnection(Map.of("dataSourceId", id.toString()));
        verify(infraManagementService).markDataSourceVerified(id);
        verify(infraManagementService, never()).getDataSourceRuntimeDetail(id);
        verify(auditService).auditActionStrict(
            eq("FOUNDATION_DATASOURCE_TEST"),
            eq(AuditStage.BEGIN),
            eq(id.toString()),
            argThat(payload -> safeConnectionAuditPayload(payload, id, "system"))
        );
        verify(auditService).auditActionStrict(
            eq("FOUNDATION_DATASOURCE_TEST"),
            eq(AuditStage.SUCCESS),
            eq(id.toString()),
            argThat(payload -> safeConnectionAuditPayload(payload, id, "system"))
        );
    }

    @Test
    void retiredGenerationApisReturnGoneWithoutInvokingLegacyServices() {
        UUID id = UUID.randomUUID();

        assertThat(resource.discoverSchema(id, Map.of(), null).getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(resource.previewOdsGeneration(id, Map.of(), null).getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(resource.precheckOdsGeneration(id, Map.of(), null).getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(resource.applyOdsGeneration(id, Map.of(), null).getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(resource.buildSyncTaskDraft(id, Map.of(), null).getStatusCode()).isEqualTo(HttpStatus.GONE);

        verify(infraManagementService, never()).getDataSource(id, null);
        verify(infraManagementService, never()).findEntity(id);
    }

    @Test
    void createRollsBackAndDoesNotWriteFailWhenSuccessAuditCannotBeFinalized() {
        UUID id = UUID.randomUUID();
        UUID beginReceipt = UUID.randomUUID();
        DataSourceRequest request = new DataSourceRequest(
            "ERP",
            "postgresql",
            "jdbc:postgresql://db:5432/erp",
            "erp",
            null,
            Map.of(),
            Map.of("password", "secret")
        );
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        when(infraManagementService.createDataSource(request, "system", null))
            .thenReturn(new InfraDataSourceDto(id, "ERP", "postgresql", "jdbc:postgresql://db:5432/erp", "erp"));
        when(auditService.auditActionStrict(anyString(), any(AuditStage.class), anyString(), any()))
            .thenAnswer(invocation -> {
                AuditStage stage = invocation.getArgument(1);
                if (stage == AuditStage.BEGIN) {
                    return beginReceipt;
                }
                if (stage == AuditStage.SUCCESS) {
                    throw new IllegalStateException("audit outbox unavailable");
                }
                return UUID.randomUUID();
            });

        assertThatThrownBy(() -> resource.create(request, null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(transactionStatus);
        verify(auditService, never()).auditActionStrict(
            eq("FOUNDATION_DATASOURCE_CREATE"),
            eq(AuditStage.FAIL),
            anyString(),
            any()
        );
    }

    @SuppressWarnings("unchecked")
    private boolean safeConnectionAuditPayload(Object value, UUID id, String operator) {
        if (!(value instanceof Map<?, ?> payload)) {
            return false;
        }
        return id.toString().equals(payload.get("dataSourceId")) &&
            operator.equals(payload.get("operator")) &&
            payload.containsKey("auditOperationId") &&
            payload.keySet().stream().noneMatch(key ->
                List.of("password", "secrets", "props", "jdbcUrl", "config").contains(String.valueOf(key))
            );
    }

    private void authenticate(String principal, String authority) {
        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    principal,
                    "n/a",
                    List.of(new SimpleGrantedAuthority(authority))
                )
            );
    }
}
