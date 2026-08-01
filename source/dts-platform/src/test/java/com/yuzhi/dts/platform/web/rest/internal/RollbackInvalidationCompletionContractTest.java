package com.yuzhi.dts.platform.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.PlatformInboundServiceAuthProperties;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.ServiceDependencyAuthenticationFilter;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationException;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService.CompletionCommand;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService.CompletionOutcome;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService.CompletionView;
import jakarta.servlet.FilterChain;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class RollbackInvalidationCompletionContractTest {

    private static final String PATH = "/api/internal/rollback-invalidation/completions";
    private static final String TOKEN = "ingestion-rollback-completion-secret";
    private static final UUID RECEIPT_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resourceExposesOnlyTheExactPostPathToTheIngestionServicePrincipal() throws Exception {
        RequestMapping mapping = RollbackInvalidationCompletionResource.class.getAnnotation(RequestMapping.class);
        PreAuthorize authorization = RollbackInvalidationCompletionResource.class.getAnnotation(PreAuthorize.class);
        Method complete = RollbackInvalidationCompletionResource.class.getDeclaredMethod(
            "complete",
            RollbackInvalidationCompletionRequest.class
        );

        assertThat(mapping.value()).containsExactly(PATH);
        assertThat(complete.getAnnotation(PostMapping.class)).isNotNull();
        assertThat(authorization.value())
            .contains(AuthoritiesConstants.SERVICE_INTERNAL)
            .contains("service:dts-ingestion");

        assertThat(authenticate("dts-ingestion", TOKEN, "POST", PATH))
            .extracting(Authentication::getName)
            .isEqualTo("service:dts-ingestion");
        assertThat(authenticate("dts-ingestion", TOKEN, "GET", PATH)).isNull();
        assertThat(authenticate("dts-ingestion", TOKEN, "POST", PATH + "/extra")).isNull();
        assertThat(authenticate("dts-analytics", "analytics-secret", "POST", PATH)).isNull();
        assertThat(authenticate("dts-ingestion", TOKEN, "POST", "/api/etl/dbt/run")).isNull();
    }

    @Test
    void requestDelegatesTheAuthoritativeCompletionIdentityWithoutRewritingIt() {
        RollbackInvalidationService service = mock(RollbackInvalidationService.class);
        RollbackInvalidationCompletionResource resource = new RollbackInvalidationCompletionResource(service);
        RollbackInvalidationCompletionRequest request = new RollbackInvalidationCompletionRequest(
            RECEIPT_ID,
            "ingestion.rollback.42.applied",
            11L,
            CompletionOutcome.APPLY,
            "ingestion transaction committed",
            false,
            "operation-42"
        );
        CompletionView expected = new CompletionView(
            RECEIPT_ID,
            RollbackInvalidationService.APPLIED,
            11L,
            2,
            false
        );
        when(service.complete(request.toCommand())).thenReturn(expected);

        assertThat(resource.complete(request)).isEqualTo(expected);
        verify(service).complete(
            new CompletionCommand(
                RECEIPT_ID,
                "ingestion.rollback.42.applied",
                11L,
                CompletionOutcome.APPLY,
                "ingestion transaction committed",
                false,
                "operation-42"
            )
        );
    }

    @Test
    void endpointPreservesTypedConflictCodesAndMapsMalformedCommandsToBadRequest() {
        RollbackInvalidationCompletionResource resource = new RollbackInvalidationCompletionResource(
            mock(RollbackInvalidationService.class)
        );
        RollbackInvalidationException conflict = RollbackInvalidationException.conflict("different payload");

        var conflictResponse = resource.handleRollbackInvalidation(conflict);
        assertThat(conflictResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflictResponse.getBody())
            .containsEntry("code", "ROLLBACK_INVALIDATION_IDEMPOTENCY_CONFLICT")
            .containsEntry("message", "different payload");

        var badRequest = resource.handleInvalidRequest(new IllegalArgumentException("eventId is invalid"));
        assertThat(badRequest.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badRequest.getBody())
            .containsEntry("code", "ROLLBACK_INVALIDATION_REQUEST_INVALID")
            .containsEntry("message", "eventId is invalid");
    }

    private Authentication authenticate(String service, String token, String method, String path) throws Exception {
        SecurityContextHolder.clearContext();
        PlatformInboundServiceAuthProperties properties = new PlatformInboundServiceAuthProperties();
        properties.setTrustedServices(Map.of("dts-ingestion", TOKEN, "dts-analytics", "analytics-secret"));
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-DTS-Service", service);
        request.addHeader("X-DTS-Service-Token", token);
        request.setMethod(method);
        request.setRequestURI(path);
        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
