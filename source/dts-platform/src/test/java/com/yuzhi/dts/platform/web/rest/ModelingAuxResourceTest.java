package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.modeling.ModelingPlan;
import com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm;
import com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTermVersion;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
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
import com.yuzhi.dts.platform.service.catalog.CatalogAssetIdentity;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.catalog.CodeAssetGrantWriter;
import com.yuzhi.dts.platform.service.modeling.DataStandardSecurity;
import com.yuzhi.dts.platform.service.modeling.ModelingAssetReferenceService;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import com.yuzhi.dts.platform.web.rest.errors.BadRequestAlertException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.prepost.PreAuthorize;

class ModelingAuxResourceTest {

    @Test
    void deleteGlossaryTermUsesSameMaintainerPermissionAsGlossaryUpsert() throws NoSuchMethodException {
        PreAuthorize createGuard = ModelingAuxResource.class
            .getMethod("createGlossaryTerm", ModelingGlossaryTerm.class, String.class)
            .getAnnotation(PreAuthorize.class);
        PreAuthorize deleteGuard = ModelingAuxResource.class
            .getMethod("deleteGlossaryTerm", UUID.class)
            .getAnnotation(PreAuthorize.class);

        assertThat(deleteGuard).isNotNull();
        assertThat(deleteGuard.value()).isEqualTo(createGuard.value());
    }

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
            mock(CatalogDomainRepository.class),
            catalogAccessChecker,
            referenceService,
            mock(CodeAssetGrantWriter.class)
        );

        ModelingPlan request = new ModelingPlan();
        request.setName("PRJ1");

        assertThatThrownBy(() -> resource.createPlan(request, "D1"))
            .isInstanceOf(BadRequestAlertException.class)
            .hasMessageContaining("已存在同名项目空间");

        verify(planRepo, never()).save(any(ModelingPlan.class));
    }

    @Test
    void createPlan_shouldPersistDomainIdAndIgnoreLegacyDomainText() {
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
        CatalogDomainRepository catalogDomainRepo = mock(CatalogDomainRepository.class);
        AccessChecker catalogAccessChecker = mock(AccessChecker.class);
        ModelingAssetReferenceService referenceService = mock(ModelingAssetReferenceService.class);
        UUID domainId = UUID.randomUUID();

        when(security.hasInstituteScope()).thenReturn(false);
        when(security.resolveActiveDept(anyString())).thenReturn("D1");
        when(planRepo.findFirstByNameIgnoreCase("ERP Plan")).thenReturn(Optional.empty());
        when(catalogDomainRepo.existsById(domainId)).thenReturn(true);
        when(planRepo.save(any(ModelingPlan.class))).thenAnswer(invocation -> {
            ModelingPlan plan = invocation.getArgument(0);
            plan.setId(UUID.randomUUID());
            return plan;
        });

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
            catalogDomainRepo,
            catalogAccessChecker,
            referenceService,
            mock(CodeAssetGrantWriter.class)
        );

        ModelingPlan request = new ModelingPlan();
        request.setName("ERP Plan");
        request.setDomain("销售域");
        request.setDomainId(domainId);
        request.setOwnerDept("D1");

        resource.createPlan(request, "D1");

        ArgumentCaptor<ModelingPlan> planCaptor = ArgumentCaptor.forClass(ModelingPlan.class);
        verify(planRepo).save(planCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(planCaptor.getValue().getDomainId()).isEqualTo(domainId);
        org.assertj.core.api.Assertions.assertThat(planCaptor.getValue().getDomain()).isNull();
    }

    @Test
    void createGlossaryTermSyncsCodeAssetGrant() {
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
        CodeAssetGrantWriter codeAssetGrantWriter = mock(CodeAssetGrantWriter.class);

        when(security.resolveActiveDept("D01")).thenReturn("D01");
        when(glossaryRepo.save(any(ModelingGlossaryTerm.class))).thenAnswer(invocation -> {
            ModelingGlossaryTerm term = invocation.getArgument(0);
            term.setId(UUID.fromString("11111111-2222-3333-4444-555555555555"));
            return term;
        });
        when(glossaryVersionRepo.findByTermAndVersion(any(ModelingGlossaryTerm.class), anyString())).thenReturn(Optional.empty());
        when(glossaryVersionRepo.save(any(ModelingGlossaryTermVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
            mock(CatalogDomainRepository.class),
            catalogAccessChecker,
            referenceService,
            codeAssetGrantWriter
        );

        ModelingGlossaryTerm request = new ModelingGlossaryTerm();
        request.setCode("contract_amount");
        request.setName("合同金额");
        request.setOwnerDept("D01");
        request.setStatus("ACTIVE");

        resource.createGlossaryTerm(request, "D01");

        ArgumentCaptor<CatalogAssetIdentity> identityCaptor = ArgumentCaptor.forClass(CatalogAssetIdentity.class);
        verify(codeAssetGrantWriter).upsertCodeAsset(identityCaptor.capture(), anyString(), anyString(), anyString(), anyString());
        CatalogAssetIdentity identity = identityCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(identity.type()).isEqualTo(CatalogAssetType.GLOSSARY_TERM);
        org.assertj.core.api.Assertions.assertThat(identity.assetId()).isEqualTo("11111111-2222-3333-4444-555555555555");
    }
}
