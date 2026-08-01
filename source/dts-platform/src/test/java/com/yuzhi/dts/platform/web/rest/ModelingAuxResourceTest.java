package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm;
import com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTermVersion;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.repository.modeling.DataStandardRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingGlossaryTermRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingGlossaryTermReviewRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingGlossaryTermVersionRepository;
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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

class ModelingAuxResourceTest {

    @Test
    void legacyPlanRoutesHaveNoControllerMapping() {
        java.util.List<String> legacyMappings = java.util.Arrays
            .stream(ModelingAuxResource.class.getDeclaredMethods())
            .map(method -> AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class))
            .filter(java.util.Objects::nonNull)
            .flatMap(mapping -> Stream.concat(java.util.Arrays.stream(mapping.path()), java.util.Arrays.stream(mapping.value())))
            .filter(path -> path.startsWith("/plans"))
            .toList();

        assertThat(legacyMappings).isEmpty();
    }

    @Test
    void constructorHasNoLegacyPlanRepositoryDependency() {
        java.util.List<String> constructorDependencies = java.util.Arrays
            .stream(ModelingAuxResource.class.getDeclaredConstructors())
            .flatMap(constructor -> java.util.Arrays.stream(constructor.getParameterTypes()))
            .map(Class::getName)
            .toList();

        assertThat(constructorDependencies).noneMatch(type -> type.contains("ModelingPlan"));
    }

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
    void deleteGlossaryTermRemovesReviewsAndVersionsBeforeTerm() {
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
        UUID id = UUID.randomUUID();
        ModelingGlossaryTerm term = new ModelingGlossaryTerm();
        term.setId(id);
        term.setName("test");

        when(glossaryRepo.findById(id)).thenReturn(Optional.of(term));
        when(referenceService.glossaryReferences(term)).thenReturn(java.util.Map.of());
        when(referenceService.countReferences(any())).thenReturn(0);

        ModelingAuxResource resource = new ModelingAuxResource(
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
            referenceService,
            mock(CodeAssetGrantWriter.class)
        );

        resource.deleteGlossaryTerm(id);

        InOrder inOrder = Mockito.inOrder(glossaryReviewRepo, glossaryVersionRepo, glossaryRepo);
        inOrder.verify(glossaryReviewRepo).deleteByTerm(term);
        inOrder.verify(glossaryVersionRepo).deleteByTerm(term);
        inOrder.verify(glossaryRepo).deleteById(id);
    }

    @Test
    void createGlossaryTermSyncsCodeAssetGrant() {
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
