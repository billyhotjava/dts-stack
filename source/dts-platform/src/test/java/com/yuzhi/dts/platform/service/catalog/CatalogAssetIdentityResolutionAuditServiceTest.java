package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogAssetResolutionFailure;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetResolutionFailureRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CatalogAssetIdentityResolutionAuditServiceTest {

    @Mock
    private CatalogAssetResolutionFailureRepository repository;

    @Test
    void recordFailurePersistsNormalizedAuditRecord() {
        CatalogAssetIdentityResolutionAuditService service = new CatalogAssetIdentityResolutionAuditService(repository);

        service.recordFailure(" gov_indicator:missing_metric ", "MetricPackPreview", "TYPE_REPOSITORY_MISS");

        ArgumentCaptor<CatalogAssetResolutionFailure> captor = ArgumentCaptor.forClass(CatalogAssetResolutionFailure.class);
        verify(repository).save(captor.capture());
        CatalogAssetResolutionFailure failure = captor.getValue();
        assertThat(failure.getRef()).isEqualTo("gov_indicator:missing_metric");
        assertThat(failure.getCaller()).isEqualTo("MetricPackPreview");
        assertThat(failure.getTypeHintGuess()).isEqualTo("gov_indicator");
        assertThat(failure.getReason()).isEqualTo("TYPE_REPOSITORY_MISS");
        assertThat(failure.getRequestedAt()).isNotNull();
    }

    @Test
    void recentFailuresUsesSinceFilterWhenProvided() {
        Instant since = Instant.parse("2026-05-17T00:00:00Z");
        CatalogAssetIdentityResolutionAuditService service = new CatalogAssetIdentityResolutionAuditService(repository);
        when(repository.findByRequestedAtGreaterThanEqualOrderByRequestedAtDesc(any(), any(Pageable.class))).thenReturn(List.of());

        List<CatalogAssetResolutionFailure> result = service.recentFailures(since, 1000);

        assertThat(result).isEmpty();
        verify(repository).findByRequestedAtGreaterThanEqualOrderByRequestedAtDesc(any(), any(Pageable.class));
    }
}
