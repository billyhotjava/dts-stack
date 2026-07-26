package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.analytics.domain.AnalyticsPublicLink;
import com.yuzhi.dts.analytics.repository.AnalyticsPublicLinkRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class PublicLinkServiceTest {

    private AnalyticsPublicLinkRepository repository;
    private AnalyticsConsumerClassificationService classificationService;
    private ScreenAuditService auditService;
    private PublicLinkService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(AnalyticsPublicLinkRepository.class);
        classificationService = Mockito.mock(AnalyticsConsumerClassificationService.class);
        auditService = Mockito.mock(ScreenAuditService.class);
        service = new PublicLinkService(repository, classificationService, auditService);
    }

    @Test
    void stale_screen_link_is_denied_and_audited() {
        AnalyticsPublicLink link = link(PublicLinkService.MODEL_SCREEN, 7L, "CONFIDENTIAL");
        Mockito.doThrow(new IllegalStateException("snapshot changed"))
            .when(classificationService)
            .requireCurrentPublicLink("public-7");

        boolean allowed = service.canAccess(link, null, "CONFIDENTIAL");

        assertThat(allowed).isFalse();
        verify(auditService).log(
            eq(7L),
            eq(null),
            eq("screen.public_link.access.denied"),
            eq(null),
            any(),
            eq(null)
        );
    }

    @Test
    void current_screen_link_still_requires_personnel_clearance() {
        AnalyticsPublicLink link = link(PublicLinkService.MODEL_SCREEN, 7L, "CONFIDENTIAL");

        boolean allowed = service.canAccess(link, null, "SECRET");

        assertThat(allowed).isFalse();
        verify(classificationService).requireCurrentPublicLink("public-7");
    }

    @Test
    void public_screen_link_allows_anonymous_classification_after_snapshot_check() {
        AnalyticsPublicLink link = link(PublicLinkService.MODEL_SCREEN, 7L, "PUBLIC");

        boolean allowed = service.canAccess(link, null, null);

        assertThat(allowed).isTrue();
        verify(classificationService).requireCurrentPublicLink("public-7");
        verify(auditService, never()).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    void creation_uses_derived_classification_and_binds_snapshot() {
        when(repository.findByModelAndModelId(PublicLinkService.MODEL_SCREEN, 7L))
            .thenReturn(Optional.empty());
        when(classificationService.ensurePublicConsumer(PublicLinkService.MODEL_SCREEN, 7L))
            .thenReturn(new AnalyticsClassificationClient.ClassificationResult(
                "SCREEN",
                "screen:7",
                "CONFIDENTIAL",
                "snapshot-7",
                3L,
                List.of(),
                null,
                List.of()
            ));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        String uuid = service.getOrCreateScoped(
            PublicLinkService.MODEL_SCREEN,
            7L,
            12L,
            null,
            "PUBLIC"
        );

        ArgumentCaptor<AnalyticsPublicLink> saved = ArgumentCaptor.forClass(AnalyticsPublicLink.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getClassification()).isEqualTo("CONFIDENTIAL");
        assertThat(uuid).isEqualTo(saved.getValue().getPublicUuid());
        verify(classificationService).bindPublicLink(
            uuid,
            PublicLinkService.MODEL_SCREEN,
            7L,
            null
        );
    }

    private AnalyticsPublicLink link(String model, long modelId, String classification) {
        AnalyticsPublicLink link = new AnalyticsPublicLink();
        link.setModel(model);
        link.setModelId(modelId);
        link.setPublicUuid("public-7");
        link.setClassification(classification);
        return link;
    }
}
