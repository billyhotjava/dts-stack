package com.yuzhi.dts.admin.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.admin.domain.audit.AuditClassificationMiss;
import com.yuzhi.dts.admin.repository.audit.AuditActionCatalogRepository;
import com.yuzhi.dts.admin.repository.audit.AuditClassificationMissRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Example;

class AuditActionCatalogServiceTest {

    private AuditClassificationMissRepository missRepository;
    private AuditActionCatalogService service;

    @BeforeEach
    void setUp() {
        missRepository = mock(AuditClassificationMissRepository.class);
        service = new AuditActionCatalogService(mock(AuditActionCatalogRepository.class), missRepository);
        when(missRepository.save(any(AuditClassificationMiss.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void incrementsExistingClassificationMissInsteadOfCreatingDuplicates() {
        AuditActionRequest request = AuditActionRequest
            .builder("xiezm", "NEW_PLATFORM_ACTION")
            .sourceSystem("platform")
            .moduleOverride("catalog.assets", "数据资产")
            .request("/api/platform/new-module", "post")
            .build();
        AuditClassificationMiss existing = existingMiss();
        when(missRepository.findOne(any(Example.class))).thenReturn(Optional.empty(), Optional.of(existing));

        service.recordMiss(request, "NO_CATALOG_MATCH");
        service.recordMiss(request, "NO_CATALOG_MATCH");

        ArgumentCaptor<AuditClassificationMiss> captor = ArgumentCaptor.forClass(AuditClassificationMiss.class);
        verify(missRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getOccurrenceCount()).isEqualTo(1L);
        assertThat(captor.getAllValues().get(1)).isSameAs(existing);
        assertThat(existing.getOccurrenceCount()).isEqualTo(6L);
        assertThat(existing.getLastSeenAt()).isAfter(Instant.parse("2026-05-22T12:00:00Z"));
    }

    private AuditClassificationMiss existingMiss() {
        AuditClassificationMiss miss = new AuditClassificationMiss();
        miss.setSourceSystem("platform");
        miss.setActionCode("NEW_PLATFORM_ACTION");
        miss.setRequestUri("/api/platform/new-module");
        miss.setHttpMethod("POST");
        miss.setActorId("xiezm");
        miss.setReason("NO_CATALOG_MATCH");
        miss.setFirstSeenAt(Instant.parse("2026-05-22T12:00:00Z"));
        miss.setLastSeenAt(Instant.parse("2026-05-22T12:00:00Z"));
        miss.setOccurrenceCount(5L);
        return miss;
    }
}
