package com.yuzhi.dts.platform.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogAssetResolutionFailure;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetIdentityResolutionAuditService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class CatalogAssetResolutionFailureResourceTest {

    @Test
    void listsRecentResolutionFailures() {
        CatalogAssetIdentityResolutionAuditService auditService = mock(CatalogAssetIdentityResolutionAuditService.class);
        Instant since = Instant.parse("2026-05-17T00:00:00Z");
        CatalogAssetResolutionFailure failure = new CatalogAssetResolutionFailure();
        UUID id = UUID.randomUUID();
        failure.setId(id);
        failure.setRef("gov_indicator:missing_metric");
        failure.setRequestedAt(since);
        failure.setCaller("MetricPackPreview");
        failure.setTypeHintGuess("gov_indicator");
        failure.setReason("TYPE_REPOSITORY_MISS");
        when(auditService.recentFailures(since, 50)).thenReturn(List.of(failure));

        CatalogAssetResolutionFailureResource resource = new CatalogAssetResolutionFailureResource(auditService);
        List<CatalogAssetResolutionFailureResource.FailureResponse> response = resource.failures(since, 50).getBody();

        assertThat(response).isNotNull();
        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(id);
        assertThat(response.get(0).ref()).isEqualTo("gov_indicator:missing_metric");
        assertThat(response.get(0).reason()).isEqualTo("TYPE_REPOSITORY_MISS");
    }

    @Test
    void requiresServiceInternalAuthority() {
        PreAuthorize preAuthorize = CatalogAssetResolutionFailureResource.class.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).contains(AuthoritiesConstants.SERVICE_INTERNAL);
    }
}
