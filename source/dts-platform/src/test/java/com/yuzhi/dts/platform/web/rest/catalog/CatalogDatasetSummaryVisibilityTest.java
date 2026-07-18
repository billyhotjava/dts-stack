package com.yuzhi.dts.platform.web.rest.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.catalog.CatalogClassificationMappingRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogMaskingRuleRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainVisibilityService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogDatasetSummaryVisibilityTest {

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private CatalogDomainRepository domainRepository;

    @Mock
    private CatalogDomainVisibilityService visibilityService;

    @Mock
    private CatalogMaskingRuleRepository maskingRepository;

    @Mock
    private CatalogClassificationMappingRepository mappingRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private CatalogDatasetResource resource;

    @Test
    void summaryCountsOnlyDomainsVisibleToTheCurrentActor() throws Exception {
        lenient().when(domainRepository.count()).thenReturn(9L);
        when(visibilityService.countVisible()).thenReturn(2L);
        when(datasetRepository.count()).thenReturn(12L);
        when(maskingRepository.count()).thenReturn(3L);
        when(mappingRepository.count()).thenReturn(4L);

        Map<String, Object> summary = resource.summary().getData();

        assertThat(summary).containsEntry("domains", 2L).containsEntry("datasets", 12L);
        verifyNoInteractions(domainRepository);
    }
}
