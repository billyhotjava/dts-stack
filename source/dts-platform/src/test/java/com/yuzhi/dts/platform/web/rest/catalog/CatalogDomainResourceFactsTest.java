package com.yuzhi.dts.platform.web.rest.catalog;

import static com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy.PUBLIC;
import static com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy.RESTRICTED;
import static com.yuzhi.dts.platform.domain.catalog.CatalogDomainLifecycleStatus.ACTIVE;
import static com.yuzhi.dts.platform.domain.catalog.CatalogDomainLifecycleStatus.ARCHIVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainVisibilityService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CatalogDomainResourceFactsTest {

    private final CatalogDomainRepository domainRepository = mock(CatalogDomainRepository.class);
    private final CatalogDatasetRepository datasetRepository = mock(CatalogDatasetRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final CatalogDomainVisibilityService visibilityService = mock(CatalogDomainVisibilityService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private CatalogDomainResource resource;

    @BeforeEach
    void setUp() {
        resource = new CatalogDomainResource(domainRepository, datasetRepository, auditService, visibilityService);
        when(domainRepository.save(any(CatalogDomain.class))).thenAnswer(invocation -> {
            CatalogDomain domain = invocation.getArgument(0);
            if (domain.getId() == null) {
                domain.setId(UUID.randomUUID());
            }
            return domain;
        });
    }

    @Test
    void createDefaultsLifecycleAndAccessPolicyWithoutChangingExistingJsonFields() throws Exception {
        CatalogDomain request = new CatalogDomain();
        request.setName("项目管理");
        request.setCode("project_management");

        CatalogDomain saved = resource.createDomain(request).getData();
        String json = objectMapper.writeValueAsString(saved);

        assertThat(saved.getLifecycleStatus()).isEqualTo(ACTIVE);
        assertThat(saved.getAccessPolicy()).isEqualTo(PUBLIC);
        assertThat(json)
            .contains("\"name\":\"项目管理\"")
            .contains("\"code\":\"project_management\"")
            .contains("\"lifecycleStatus\":\"ACTIVE\"")
            .contains("\"accessPolicy\":\"PUBLIC\"");
    }

    @Test
    void createRejectsClientSuppliedIdBeforeRepositorySave() {
        CatalogDomain request = domain(UUID.randomUUID(), ACTIVE, PUBLIC);

        assertThatThrownBy(() -> resource.createDomain(request))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(exception.getReason()).isEqualTo("New catalog domain must not include id");
            });

        verify(domainRepository, never()).save(any(CatalogDomain.class));
    }

    @Test
    void updateMaintainsStrictLifecycleAndAccessPolicyEnums() {
        UUID id = UUID.randomUUID();
        CatalogDomain existing = domain(id, ACTIVE, PUBLIC);
        CatalogDomain patch = domain(null, ARCHIVED, RESTRICTED);
        when(domainRepository.findById(id)).thenReturn(Optional.of(existing));
        when(visibilityService.canMaintain(any(CatalogDomain.class))).thenReturn(true);

        CatalogDomain saved = resource.updateDomain(id, patch).getData();

        assertThat(saved.getLifecycleStatus()).isEqualTo(ARCHIVED);
        assertThat(saved.getAccessPolicy()).isEqualTo(RESTRICTED);
    }

    @Test
    void restrictedCreateOrTransitionRequiresAnExistingAccessFact() {
        CatalogDomain create = domain(null, ACTIVE, RESTRICTED);
        CatalogDomain existing = domain(UUID.randomUUID(), ACTIVE, PUBLIC);
        CatalogDomain patch = domain(null, ACTIVE, RESTRICTED);
        when(domainRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(visibilityService.canMaintain(any(CatalogDomain.class))).thenReturn(false);

        assertThatThrownBy(() -> resource.createDomain(create))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403 FORBIDDEN");
        assertThatThrownBy(() -> resource.updateDomain(existing.getId(), patch))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403 FORBIDDEN");

        verify(auditService, never()).auditAction(
            org.mockito.ArgumentMatchers.eq("CATALOG_DOMAIN_CREATE"),
            any(),
            any(),
            any()
        );
        verify(auditService, never()).auditAction(
            org.mockito.ArgumentMatchers.eq("CATALOG_DOMAIN_UPDATE"),
            any(),
            any(),
            any()
        );
    }

    @Test
    void restrictedCreateSucceedsForAnActorWithARealGlobalOrExplicitAccessFact() {
        CatalogDomain create = domain(null, ACTIVE, RESTRICTED);
        when(visibilityService.canMaintain(any(CatalogDomain.class))).thenReturn(true);

        CatalogDomain saved = resource.createDomain(create).getData();

        assertThat(saved.getAccessPolicy()).isEqualTo(RESTRICTED);
        verify(auditService).auditAction(
            org.mockito.ArgumentMatchers.eq("CATALOG_DOMAIN_CREATE"),
            any(),
            org.mockito.ArgumentMatchers.eq(saved.getId().toString()),
            any()
        );
    }

    @Test
    void unauthorizedMaintainerCannotDeclassifyDeleteOrMoveAnExistingRestrictedDomain() {
        CatalogDomain restricted = domain(UUID.randomUUID(), ACTIVE, RESTRICTED);
        CatalogDomain publicPatch = domain(null, ACTIVE, PUBLIC);
        when(domainRepository.findById(restricted.getId())).thenReturn(Optional.of(restricted));
        when(visibilityService.canMaintain(restricted)).thenReturn(false);

        assertThatThrownBy(() -> resource.updateDomain(restricted.getId(), publicPatch))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403 FORBIDDEN");
        assertThat(restricted.getAccessPolicy()).isEqualTo(RESTRICTED);
        assertThatThrownBy(() -> resource.deleteDomain(restricted.getId()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403 FORBIDDEN");
        assertThatThrownBy(() -> resource.moveDomain(restricted.getId(), Map.of("newParentId", "")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403 FORBIDDEN");

        verify(domainRepository, never()).deleteById(restricted.getId());
        verify(domainRepository, never()).save(restricted);
        verify(auditService, never()).auditAction(
            org.mockito.ArgumentMatchers.matches("CATALOG_DOMAIN_(UPDATE|DELETE|MOVE)"),
            any(),
            any(),
            any()
        );
    }

    @Test
    void updateOrMoveCannotAttachAVisibleDomainToAnUnauthorizedRestrictedParent() {
        CatalogDomain target = domain(UUID.randomUUID(), ACTIVE, PUBLIC);
        CatalogDomain parentRef = new CatalogDomain();
        parentRef.setId(UUID.randomUUID());
        CatalogDomain patch = domain(null, ACTIVE, PUBLIC);
        patch.setParent(parentRef);
        when(domainRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(visibilityService.findVisibleById(parentRef.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resource.updateDomain(target.getId(), patch))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404 NOT_FOUND");
        assertThat(target.getParent()).isNull();
        assertThatThrownBy(() -> resource.moveDomain(target.getId(), Map.of("newParentId", parentRef.getId().toString())))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404 NOT_FOUND");
        assertThat(target.getParent()).isNull();

        verify(domainRepository, never()).save(target);
    }

    @Test
    void invalidLifecycleOrAccessPolicyCannotBeDeserialized() {
        assertThatThrownBy(() -> objectMapper.readValue("{\"name\":\"x\",\"lifecycleStatus\":\"DELETED\"}", CatalogDomain.class))
            .hasMessageContaining("DELETED");
        assertThatThrownBy(() -> objectMapper.readValue("{\"name\":\"x\",\"accessPolicy\":\"PRIVATE\"}", CatalogDomain.class))
            .hasMessageContaining("PRIVATE");
        assertThatThrownBy(() -> objectMapper.readValue("{\"name\":\"x\",\"lifecycleStatus\":null}", CatalogDomain.class))
            .hasMessageContaining("lifecycleStatus must not be null");
        assertThatThrownBy(() -> objectMapper.readValue("{\"name\":\"x\",\"accessPolicy\":null}", CatalogDomain.class))
            .hasMessageContaining("accessPolicy must not be null");
    }

    @Test
    void treeExposesLifecycleAndAccessFactsWithoutDroppingExistingFields() {
        CatalogDomain domain = domain(UUID.randomUUID(), ARCHIVED, RESTRICTED);
        when(visibilityService.findAllVisible()).thenReturn(List.of(domain));

        List<Map<String, Object>> tree = resource.getDomainTree().getData();

        assertThat(tree).singleElement().satisfies(node -> {
            assertThat(node).containsEntry("id", domain.getId());
            assertThat(node).containsEntry("name", domain.getName());
            assertThat(node).containsEntry("code", domain.getCode());
            assertThat(node).containsEntry("lifecycleStatus", ARCHIVED);
            assertThat(node).containsEntry("accessPolicy", RESTRICTED);
        });
    }

    @Test
    void listUsesVisibilityScopedPageAndDoesNotLeakHiddenTotal() {
        CatalogDomain visible = domain(UUID.randomUUID(), ACTIVE, PUBLIC);
        PageRequest pageable = PageRequest.of(
            0,
            10,
            org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("createdDate"),
                org.springframework.data.domain.Sort.Order.asc("id")
            )
        );
        when(visibilityService.findVisiblePage("project", pageable)).thenReturn(new PageImpl<>(List.of(visible), pageable, 1));

        Map<String, Object> data = resource.listDomains(0, 10, " project ").getData();

        assertThat(data).containsEntry("total", 1L);
        assertThat((List<?>) data.get("content")).singleElement().satisfies(item -> {
            assertThat(item).isInstanceOf(CatalogDomainResource.CatalogDomainListItem.class);
            CatalogDomainResource.CatalogDomainListItem listItem = (CatalogDomainResource.CatalogDomainListItem) item;
            assertThat(listItem.id()).isEqualTo(visible.getId());
            assertThat(listItem.name()).isEqualTo(visible.getName());
            assertThat(listItem.code()).isEqualTo(visible.getCode());
        });
    }

    @Test
    void listAndTreeDoNotLeakAnInvisibleRestrictedParentThroughAPublicChild() throws Exception {
        CatalogDomain hiddenParent = domain(UUID.randomUUID(), ACTIVE, RESTRICTED);
        hiddenParent.setName("restricted-parent-secret");
        hiddenParent.setDescription("restricted-parent-description-secret");
        CatalogDomain visibleChild = domain(UUID.randomUUID(), ACTIVE, PUBLIC);
        visibleChild.setParent(hiddenParent);
        PageRequest pageable = PageRequest.of(
            0,
            10,
            org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("createdDate"),
                org.springframework.data.domain.Sort.Order.asc("id")
            )
        );
        when(visibilityService.findVisiblePage(null, pageable)).thenReturn(new PageImpl<>(List.of(visibleChild), pageable, 1));
        when(visibilityService.findAllVisible()).thenReturn(List.of(visibleChild));

        Map<String, Object> list = resource.listDomains(0, 10, null).getData();
        String listJson = objectMapper.writeValueAsString(list);
        List<Map<String, Object>> tree = resource.getDomainTree().getData();

        assertThat(listJson)
            .contains("\"parent\":null")
            .doesNotContain(hiddenParent.getId().toString())
            .doesNotContain("restricted-parent-secret")
            .doesNotContain("restricted-parent-description-secret");
        assertThat(tree).singleElement().satisfies(node -> assertThat(node).containsEntry("parentId", null));
    }

    @Test
    void listKeepsAVisibleParentReferenceAsIdOnly() throws Exception {
        CatalogDomain visibleParent = domain(UUID.randomUUID(), ACTIVE, PUBLIC);
        visibleParent.setName("visible-parent-name-must-not-be-embedded");
        CatalogDomain visibleChild = domain(UUID.randomUUID(), ACTIVE, PUBLIC);
        visibleChild.setParent(visibleParent);
        PageRequest pageable = PageRequest.of(
            0,
            10,
            org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("createdDate"),
                org.springframework.data.domain.Sort.Order.asc("id")
            )
        );
        when(visibilityService.findVisiblePage(null, pageable)).thenReturn(new PageImpl<>(List.of(visibleChild), pageable, 1));
        when(visibilityService.findVisibleIds(Set.of(visibleParent.getId()))).thenReturn(Set.of(visibleParent.getId()));

        String listJson = objectMapper.writeValueAsString(resource.listDomains(0, 10, null).getData());

        assertThat(listJson)
            .contains("\"parent\":{\"id\":\"" + visibleParent.getId() + "\"}")
            .doesNotContain("visible-parent-name-must-not-be-embedded");
    }

    @Test
    void assetStatsReturnsNotFoundForARestrictedDomainTheActorCannotSee() {
        UUID id = UUID.randomUUID();
        when(visibilityService.findVisibleById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resource.getDomainAssetStats(id))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404 NOT_FOUND");
    }

    private static CatalogDomain domain(
        UUID id,
        com.yuzhi.dts.platform.domain.catalog.CatalogDomainLifecycleStatus lifecycleStatus,
        com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy accessPolicy
    ) {
        CatalogDomain domain = new CatalogDomain();
        domain.setId(id);
        domain.setName("项目管理");
        domain.setCode("project_management");
        domain.setOwner("owner-1");
        domain.setDescription("项目管理分类");
        domain.setLifecycleStatus(lifecycleStatus);
        domain.setAccessPolicy(accessPolicy);
        return domain;
    }
}
