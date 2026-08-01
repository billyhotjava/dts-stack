package com.yuzhi.dts.ingestion.web.rest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.ingestion.security.AuthoritiesConstants;
import com.yuzhi.dts.ingestion.security.ForwardedUserPrincipal;
import com.yuzhi.dts.ingestion.service.IngestionTaskSecretMigrationService;
import com.yuzhi.dts.ingestion.service.IngestionTaskSecretMigrationService.CompatibilityRestoreBatch;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class IngestionTaskSecretRestoreResourceTest {

    @Mock private IngestionTaskSecretMigrationService migrationService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldResolveRestoreActorOnlyFromAuthenticatedServerContext() {
        authenticateTrustedForwardedAdmin("xiezm");
        IngestionTaskSecretRestoreResource resource = new IngestionTaskSecretRestoreResource(migrationService);
        CompatibilityRestoreBatch response = new CompatibilityRestoreBatch(
            "batch-1", false, 10, 0, 0, 0, 0, 0, List.of()
        );
        when(migrationService.restoreCompatibilityBatch("batch-1", 10, "xiezm")).thenReturn(response);

        resource.restore(new IngestionTaskSecretRestoreResource.CompatibilityRestoreRequest("batch-1", 10, true));

        verify(migrationService).restoreCompatibilityBatch("batch-1", 10, "xiezm");
    }

    @Test
    void shouldRejectServiceActorBeforeCompatibilityRestore() {
        authenticate(
            "service:dts-platform",
            AuthoritiesConstants.OP_ADMIN,
            AuthoritiesConstants.SERVICE_DTS_PLATFORM
        );
        IngestionTaskSecretRestoreResource resource = new IngestionTaskSecretRestoreResource(migrationService);

        assertThatThrownBy(() -> resource.restore(
            new IngestionTaskSecretRestoreResource.CompatibilityRestoreRequest("batch-1", 10, true)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("AUTHENTICATED_HUMAN_AUDIT_ACTOR_REQUIRED");
    }

    @Test
    void shouldRejectOrdinaryAuthenticationEvenWhenItClaimsAdmin() {
        authenticate("xiezm", AuthoritiesConstants.ADMIN);
        IngestionTaskSecretRestoreResource resource = new IngestionTaskSecretRestoreResource(migrationService);

        assertThatThrownBy(() -> resource.restore(
            new IngestionTaskSecretRestoreResource.CompatibilityRestoreRequest("batch-1", 10, true)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("AUTHENTICATED_HUMAN_AUDIT_ACTOR_REQUIRED");
    }

    @Test
    void shouldRejectReservedMachineIdentityEvenWhenForwardedByTrustedPlatform() {
        authenticateTrustedForwardedAdmin("_system:airflow");
        IngestionTaskSecretRestoreResource resource = new IngestionTaskSecretRestoreResource(migrationService);

        assertThatThrownBy(() -> resource.restore(
            new IngestionTaskSecretRestoreResource.CompatibilityRestoreRequest("batch-1", 10, true)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("AUTHENTICATED_HUMAN_AUDIT_ACTOR_REQUIRED");
    }

    private void authenticateTrustedForwardedAdmin(String actor) {
        authenticate(
            new ForwardedUserPrincipal(actor, "dts-platform"),
            AuthoritiesConstants.ADMIN,
            AuthoritiesConstants.SERVICE_DTS_PLATFORM
        );
    }

    private void authenticate(Object principal, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal,
                null,
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()
            )
        );
    }
}
