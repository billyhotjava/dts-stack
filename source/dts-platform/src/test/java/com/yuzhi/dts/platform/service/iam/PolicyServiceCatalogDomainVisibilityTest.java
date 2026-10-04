package com.yuzhi.dts.platform.service.iam;

import static com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy.PUBLIC;
import static com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy.RESTRICTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.repository.iam.IamDatasetPolicyRepository;
import com.yuzhi.dts.platform.repository.iam.IamSubjectDirectoryRepository;
import com.yuzhi.dts.platform.repository.iam.IamUserClassificationRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainVisibilityService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PolicyServiceCatalogDomainVisibilityTest {

    @Mock
    private CatalogDomainRepository domainRepository;

    @Mock
    private CatalogDomainVisibilityService visibilityService;

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private IamDatasetPolicyRepository datasetPolicyRepository;

    @Mock
    private IamSubjectDirectoryRepository subjectDirectoryRepository;

    @Mock
    private IamUserClassificationRepository userClassificationRepository;

    @InjectMocks
    private PolicyService service;

    @Test
    void authorizationTreeOmitsHiddenDomainsWithoutReclassifyingTheirDatasetsAsOrphans() {
        CatalogDomain visible = domain("公开分类", PUBLIC);
        CatalogDomain hidden = domain("受限分类机密名", RESTRICTED);
        CatalogDataset visibleDataset = dataset("公开数据", visible);
        CatalogDataset hiddenDataset = dataset("隐藏域数据", hidden);
        CatalogDataset orphanDataset = dataset("真正未归类数据", null);
        lenient().when(domainRepository.findAll()).thenReturn(List.of(visible, hidden));
        lenient().when(visibilityService.findAllVisible()).thenReturn(List.of(visible));
        when(datasetRepository.findAll()).thenReturn(List.of(visibleDataset, hiddenDataset, orphanDataset));

        List<Map<String, Object>> tree = service.domainsWithDatasets();

        assertThat(tree).hasSize(2);
        assertThat(tree.toString())
            .contains("公开分类", "公开数据", "未归属域", "真正未归类数据")
            .doesNotContain("受限分类机密名", "隐藏域数据");
    }

    private static CatalogDomain domain(
        String name,
        com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy accessPolicy
    ) {
        CatalogDomain domain = new CatalogDomain();
        domain.setId(UUID.randomUUID());
        domain.setName(name);
        domain.setCode(name);
        domain.setAccessPolicy(accessPolicy);
        return domain;
    }

    private static CatalogDataset dataset(String name, CatalogDomain domain) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(UUID.randomUUID());
        dataset.setName(name);
        dataset.setDomain(domain);
        return dataset;
    }
}
