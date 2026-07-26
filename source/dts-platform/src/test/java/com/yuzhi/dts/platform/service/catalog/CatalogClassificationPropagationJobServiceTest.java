package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationPropagationJob;
import com.yuzhi.dts.platform.repository.catalog.CatalogClassificationPropagationJobRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogClassificationPropagationJobServiceTest {

    @Test
    void failedPropagationPersistsRetryStateOutsidePropagationTransaction() {
        CatalogClassificationPropagationJobRepository jobRepository = mock(
            CatalogClassificationPropagationJobRepository.class
        );
        CatalogClassificationPropagationService propagationService = mock(CatalogClassificationPropagationService.class);
        CatalogClassificationPropagationJob job = pendingJob();
        when(
            jobRepository.findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedDateAsc(
                anyList(),
                any(Instant.class)
            )
        ).thenReturn(List.of(job));
        when(propagationService.recompute(job.getTargetDatasetId(), job.getTriggerRef()))
            .thenThrow(new CatalogClassificationException("CLASSIFICATION_UNAVAILABLE", "temporary failure"));

        service(jobRepository, propagationService).processPending();

        assertThat(job.getStatus()).isEqualTo("RETRY");
        assertThat(job.getAttempts()).isEqualTo(1);
        assertThat(job.getNextAttemptAt()).isAfter(Instant.now());
        assertThat(job.getLastError()).contains("CLASSIFICATION_UNAVAILABLE");
        verify(jobRepository, atLeast(2)).save(job);
    }

    @Test
    void missingLineageIsBlockedWithoutLosingTheFailureEvidence() {
        CatalogClassificationPropagationJobRepository jobRepository = mock(
            CatalogClassificationPropagationJobRepository.class
        );
        CatalogClassificationPropagationService propagationService = mock(CatalogClassificationPropagationService.class);
        CatalogClassificationPropagationJob job = pendingJob();
        when(
            jobRepository.findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedDateAsc(
                anyList(),
                any(Instant.class)
            )
        ).thenReturn(List.of(job));
        when(propagationService.recompute(job.getTargetDatasetId(), job.getTriggerRef()))
            .thenThrow(new CatalogClassificationException("PENDING_LINEAGE", "field lineage is incomplete"));

        service(jobRepository, propagationService).processPending();

        assertThat(job.getStatus()).isEqualTo("BLOCKED");
        assertThat(job.getNextAttemptAt()).isNull();
        assertThat(job.getLastError()).contains("PENDING_LINEAGE");
    }

    private static CatalogClassificationPropagationJobService service(
        CatalogClassificationPropagationJobRepository jobRepository,
        CatalogClassificationPropagationService propagationService
    ) {
        return new CatalogClassificationPropagationJobService(
            jobRepository,
            mock(CatalogDatasetLineageRepository.class),
            mock(CatalogDatasetRepository.class),
            propagationService
        );
    }

    private static CatalogClassificationPropagationJob pendingJob() {
        CatalogClassificationPropagationJob job = new CatalogClassificationPropagationJob();
        job.setId(UUID.randomUUID());
        job.setTargetDatasetId(UUID.randomUUID());
        job.setTargetAssetKey("dataset:sprint72");
        job.setTriggerType("TEST");
        job.setTriggerRef("sprint72-runtime");
        job.setStatus("PENDING");
        job.setAttempts(0);
        return job;
    }
}
