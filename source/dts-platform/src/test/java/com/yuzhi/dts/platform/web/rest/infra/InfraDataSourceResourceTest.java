package com.yuzhi.dts.platform.web.rest.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.DtsAdminProperties;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.infra.InfraManagementService;
import com.yuzhi.dts.platform.service.infra.JdbcCatalogSyncService;
import com.yuzhi.dts.platform.service.infra.JdbcConnectionTestService;
import com.yuzhi.dts.platform.service.infra.OdsGenerationService;
import com.yuzhi.dts.platform.service.infra.dto.InfraDataSourceDetailDto;
import com.yuzhi.dts.platform.service.services.SvcTokenAuthService;
import com.yuzhi.dts.platform.service.services.SvcTokenAuthService.TokenPrincipal;
import com.yuzhi.dts.platform.security.policy.PersonnelLevel;
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
    private JdbcCatalogSyncService jdbcCatalogSyncService;

    @Mock
    private OdsGenerationService odsGenerationService;

    @Mock
    private SvcTokenAuthService svcTokenAuthService;

    private DtsAdminProperties properties;
    private InfraDataSourceResource resource;

    @BeforeEach
    void setUp() {
        properties = new DtsAdminProperties();
        properties.setServiceToken("svc-secret");
        resource = new InfraDataSourceResource(
            infraManagementService,
            auditService,
            jdbcConnectionTestService,
            jdbcCatalogSyncService,
            odsGenerationService,
            properties,
            svcTokenAuthService
        );
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
        authenticate("service:dts-ingestion", AuthoritiesConstants.OP_ADMIN);

        assertThatThrownBy(() -> resource.runtimeDetail(id, "wrong-secret"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);

        verify(infraManagementService, never()).getDataSourceRuntimeDetail(id);
    }

    @Test
    void runtimeDetailAllowsServicePrincipalWithServiceToken() {
        UUID id = UUID.randomUUID();
        authenticate("service:dts-ingestion", AuthoritiesConstants.OP_ADMIN);
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
        properties.setServiceToken(null);
        authenticate("service:dts-ingestion", AuthoritiesConstants.OP_ADMIN);
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
