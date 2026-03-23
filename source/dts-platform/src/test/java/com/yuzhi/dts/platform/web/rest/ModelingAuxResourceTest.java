package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.modeling.ModelingPlan;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.repository.modeling.DataStandardRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingGlossaryTermRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingGlossaryTermReviewRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingGlossaryTermVersionRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingPlanRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingPlanReviewRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingPlanVersionRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingTemplateRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingTemplateVersionRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.DataStandardSecurity;
import com.yuzhi.dts.platform.service.modeling.ModelingAssetReferenceService;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ModelingAuxResourceTest {

    @Test
    void createPlan_shouldRejectDuplicateNameIgnoreCase() {
        ModelingPlanRepository planRepo = mock(ModelingPlanRepository.class);
        ModelingPlanVersionRepository planVersionRepo = mock(ModelingPlanVersionRepository.class);
        ModelingPlanReviewRepository planReviewRepo = mock(ModelingPlanReviewRepository.class);
        ModelingGlossaryTermRepository glossaryRepo = mock(ModelingGlossaryTermRepository.class);
        ModelingGlossaryTermVersionRepository glossaryVersionRepo = mock(ModelingGlossaryTermVersionRepository.class);
        ModelingGlossaryTermReviewRepository glossaryReviewRepo = mock(ModelingGlossaryTermReviewRepository.class);
        ModelingTemplateRepository templateRepo = mock(ModelingTemplateRepository.class);
        ModelingTemplateVersionRepository templateVersionRepo = mock(ModelingTemplateVersionRepository.class);
        AuditService auditService = mock(AuditService.class);
        DataStandardSecurity security = mock(DataStandardSecurity.class);
        OrganizationVisibilityService organizationVisibilityService = mock(OrganizationVisibilityService.class);
        DataStandardRepository dataStandardRepository = mock(DataStandardRepository.class);
        GovIndicatorDefinitionRepository indicatorRepository = mock(GovIndicatorDefinitionRepository.class);
        CatalogTableSchemaRepository catalogTableRepo = mock(CatalogTableSchemaRepository.class);
        CatalogColumnSchemaRepository catalogColumnRepo = mock(CatalogColumnSchemaRepository.class);
        AccessChecker catalogAccessChecker = mock(AccessChecker.class);
        ModelingAssetReferenceService referenceService = mock(ModelingAssetReferenceService.class);

        when(security.hasInstituteScope()).thenReturn(false);
        when(security.resolveActiveDept(anyString())).thenReturn("D1");

        ModelingPlan existing = new ModelingPlan();
        existing.setId(UUID.randomUUID());
        existing.setName("prj1");
        when(planRepo.findFirstByNameIgnoreCase("PRJ1")).thenReturn(Optional.of(existing));

        ModelingAuxResource resource = new ModelingAuxResource(
            planRepo,
            planVersionRepo,
            planReviewRepo,
            glossaryRepo,
            glossaryVersionRepo,
            glossaryReviewRepo,
            templateRepo,
            templateVersionRepo,
            auditService,
            security,
            organizationVisibilityService,
            new ObjectMapper(),
            dataStandardRepository,
            indicatorRepository,
            catalogTableRepo,
            catalogColumnRepo,
            catalogAccessChecker,
            referenceService
        );

        ModelingPlan request = new ModelingPlan();
        request.setName("PRJ1");

        assertThatThrownBy(() -> resource.createPlan(request, "D1"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("项目空间名称已存在");

        verify(planRepo, never()).save(any(ModelingPlan.class));
    }
}
