package com.yuzhi.dts.platform.web.rest.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogMetadataChangeLogRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.modeling.DataStandardRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.config.CatalogFeatureProperties;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

class CatalogResourceHelperUnassignedDomainTest {

    @Test
    void detailDtoExposesEntityVersionWithExistingGovernanceFields() {
        CatalogResourceHelper helper = helper();
        CatalogDataset dataset = new CatalogDataset();
        ReflectionTestUtils.setField(dataset, "id", java.util.UUID.randomUUID());
        ReflectionTestUtils.setField(dataset, "version", 7L);
        dataset.setOwner("owner");
        dataset.setDescription("description");
        var dto = helper.toDatasetDto(dataset);
        assertThat(dto).containsEntry("id", dataset.getId()).containsEntry("version", 7L).containsEntry("owner", "owner").containsEntry("description", "description");
    }

    private static CatalogResourceHelper helper() {
        return new CatalogResourceHelper(
            mock(CatalogDatasetRepository.class), mock(CatalogTableSchemaRepository.class),
            mock(CatalogColumnSchemaRepository.class), mock(CatalogMetadataChangeLogRepository.class),
            mock(DataStandardRepository.class), mock(InfraDataSourceRepository.class),
            mock(CatalogFeatureProperties.class), mock(OrganizationVisibilityService.class)
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void filtersDatasetsWhoseBusinessDomainIsUnassigned() {
        CatalogResourceHelper helper = new CatalogResourceHelper(
            mock(CatalogDatasetRepository.class),
            mock(CatalogTableSchemaRepository.class),
            mock(CatalogColumnSchemaRepository.class),
            mock(CatalogMetadataChangeLogRepository.class),
            mock(DataStandardRepository.class),
            mock(InfraDataSourceRepository.class),
            mock(CatalogFeatureProperties.class),
            mock(OrganizationVisibilityService.class)
        );
        Root<CatalogDataset> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Path<Object> domainPath = mock(Path.class);
        Predicate unassignedPredicate = mock(Predicate.class);
        Predicate combined = mock(Predicate.class);
        when(root.get("domain")).thenReturn(domainPath);
        when(criteriaBuilder.isNull(domainPath)).thenReturn(unassignedPredicate);
        when(criteriaBuilder.and(unassignedPredicate)).thenReturn(combined);

        Specification<CatalogDataset> specification = helper.buildDatasetListSpecification(
            null,
            true,
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            null,
            null,
            null
        );

        specification.toPredicate(root, query, criteriaBuilder);

        verify(criteriaBuilder).isNull(domainPath);
    }
}
