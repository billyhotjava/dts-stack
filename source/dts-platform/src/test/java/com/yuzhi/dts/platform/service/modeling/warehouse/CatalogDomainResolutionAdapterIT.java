package com.yuzhi.dts.platform.service.modeling.warehouse;

import static com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy.PUBLIC;
import static com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy.RESTRICTED;
import static com.yuzhi.dts.platform.domain.catalog.CatalogDomainLifecycleStatus.ACTIVE;
import static com.yuzhi.dts.platform.domain.catalog.CatalogDomainLifecycleStatus.ARCHIVED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.ResolutionStatus.AVAILABLE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.ResolutionStatus.FORBIDDEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.domain.permission.AssetGrant;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.repository.permission.AssetGrantRepository;
import com.yuzhi.dts.platform.web.rest.catalog.CatalogDomainResource;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class CatalogDomainResolutionAdapterIT {

    @Autowired
    private CatalogDomainRepository domainRepository;

    @Autowired
    private AssetGrantRepository grantRepository;

    @Autowired
    private CatalogDomainResolutionAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CatalogDomainResource domainResource;

    @Test
    @WithMockUser(username = "alice", authorities = "ROLE_EMPLOYEE")
    void usesPersistedLifecycleAndGrantFactsForRestrictedResolution() {
        CatalogDomain restricted = save(ACTIVE, RESTRICTED);

        assertThat(adapter.resolve(restricted.getId()).status()).isEqualTo(FORBIDDEN);
        assertThat(adapter.resolve(restricted.getId()).name()).isNull();

        AssetGrant grant = new AssetGrant();
        grant.setAssetType("CATALOG_DOMAIN");
        grant.setAssetId(restricted.getId().toString());
        grant.setGranteeType("USER");
        grant.setGranteeId("alice");
        grant.setPermission("READ");
        grant.setGrantedBy("integration-test");
        grantRepository.saveAndFlush(grant);

        assertThat(adapter.resolve(restricted.getId()).status()).isEqualTo(AVAILABLE);
        assertThat(adapter.resolve(restricted.getId()).name()).isEqualTo("集成测试分类");
    }

    @Test
    void databaseDefaultsAndCheckConstraintsProtectCatalogFacts() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into catalog_domain (id, name) values (?, ?)", id, "历史分类");

        assertThat(jdbcTemplate.queryForObject("select lifecycle_status from catalog_domain where id = ?", String.class, id))
            .isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("select access_policy from catalog_domain where id = ?", String.class, id))
            .isEqualTo("PUBLIC");
        assertThatThrownBy(() -> jdbcTemplate.update("update catalog_domain set lifecycle_status = 'DELETED' where id = ?", id))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @WithMockUser(username = "alice", authorities = "ROLE_EMPLOYEE")
    void publicArchivedDomainDoesNotRequireAnObjectGrant() {
        CatalogDomain archived = save(ARCHIVED, PUBLIC);

        assertThat(adapter.resolve(archived.getId()).status())
            .isEqualTo(CatalogDomainResolutionPort.ResolutionStatus.ARCHIVED);
    }

    @Test
    @WithMockUser(username = "alice", authorities = "ROLE_EMPLOYEE")
    void catalogReadApisExcludeUnauthorizedRestrictedDomainsBeforePagination() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        CatalogDomain visible = save("scope_" + suffix, ACTIVE, PUBLIC);
        CatalogDomain hidden = save("scope_" + suffix, ACTIVE, RESTRICTED);

        var firstPage = domainResource.listDomains(0, 10, "scope_" + suffix).getData();
        assertThat(firstPage.get("total")).isEqualTo(1L);
        assertThat(singleListItem(firstPage).id()).isEqualTo(visible.getId());
        assertThat(flattenIds(domainResource.getDomainTree().getData())).contains(visible.getId()).doesNotContain(hidden.getId());
        assertThatThrownBy(() -> domainResource.getDomainAssetStats(hidden.getId()))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("404 NOT_FOUND");

        grantRead(hidden, "alice");

        var grantedPage = domainResource.listDomains(0, 10, "scope_" + suffix).getData();
        assertThat(grantedPage.get("total")).isEqualTo(2L);
        assertThat(flattenIds(domainResource.getDomainTree().getData())).contains(visible.getId(), hidden.getId());
        assertThat(domainResource.getDomainAssetStats(hidden.getId()).getData()).containsEntry("datasetCount", 0L);
    }

    @Test
    @WithMockUser(username = "institute-leader", authorities = "ROLE_INST_LEADER")
    void catalogReadApisHonorInstituteWideAllScopeForRestrictedDomains() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        CatalogDomain restricted = save("all_scope_" + suffix, ACTIVE, RESTRICTED);

        var page = domainResource.listDomains(0, 10, "all_scope_" + suffix).getData();

        assertThat(page.get("total")).isEqualTo(1L);
        assertThat(singleListItem(page).id()).isEqualTo(restricted.getId());
    }

    @Test
    @WithMockUser(username = "alice", authorities = "ROLE_EMPLOYEE")
    void publicChildDoesNotLeakItsRestrictedParentUntilTheParentIsGranted() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        CatalogDomain hiddenParent = save("hidden_parent_" + suffix, ACTIVE, RESTRICTED);
        CatalogDomain publicChild = save("public_child_" + suffix, ACTIVE, PUBLIC);
        publicChild.setParent(hiddenParent);
        domainRepository.saveAndFlush(publicChild);

        var hiddenParentPage = domainResource.listDomains(0, 10, "public_child_" + suffix).getData();
        CatalogDomainResource.CatalogDomainListItem hiddenParentItem = singleListItem(hiddenParentPage);
        java.util.Map<String, Object> hiddenParentTreeNode = findNode(domainResource.getDomainTree().getData(), publicChild.getId());

        assertThat(hiddenParentItem.parent()).isNull();
        assertThat(hiddenParentTreeNode).containsEntry("parentId", null);

        grantRead(hiddenParent, "alice");

        var visibleParentPage = domainResource.listDomains(0, 10, "public_child_" + suffix).getData();
        CatalogDomainResource.CatalogDomainListItem visibleParentItem = singleListItem(visibleParentPage);
        java.util.Map<String, Object> visibleParentTreeNode = findNode(domainResource.getDomainTree().getData(), publicChild.getId());

        assertThat(visibleParentItem.parent()).isEqualTo(new CatalogDomainResource.CatalogDomainParentRef(hiddenParent.getId()));
        assertThat(visibleParentTreeNode).containsEntry("parentId", hiddenParent.getId());
    }

    @Test
    @WithMockUser(username = "institute-owner", authorities = "ROLE_INST_DATA_OWNER")
    void createCannotMergeOrDeclassifyAnExistingRestrictedUuid() {
        CatalogDomain existing = save(ACTIVE, RESTRICTED);
        CatalogDomain forgedCreate = new CatalogDomain();
        forgedCreate.setId(existing.getId());
        forgedCreate.setName("攻击者覆盖名称");
        forgedCreate.setCode(existing.getCode());
        forgedCreate.setLifecycleStatus(ACTIVE);
        forgedCreate.setAccessPolicy(PUBLIC);

        assertThatThrownBy(() -> domainResource.createDomain(forgedCreate))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("400 BAD_REQUEST");

        assertThat(
            jdbcTemplate.queryForObject(
                "select name from catalog_domain where id = ?",
                String.class,
                existing.getId()
            )
        ).isEqualTo("集成测试分类");
        assertThat(
            jdbcTemplate.queryForObject(
                "select access_policy from catalog_domain where id = ?",
                String.class,
                existing.getId()
            )
        ).isEqualTo("RESTRICTED");
    }

    private CatalogDomain save(
        com.yuzhi.dts.platform.domain.catalog.CatalogDomainLifecycleStatus lifecycleStatus,
        com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy accessPolicy
    ) {
        return save("it_" + UUID.randomUUID().toString().replace("-", ""), lifecycleStatus, accessPolicy);
    }

    private CatalogDomain save(
        String code,
        com.yuzhi.dts.platform.domain.catalog.CatalogDomainLifecycleStatus lifecycleStatus,
        com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy accessPolicy
    ) {
        CatalogDomain domain = new CatalogDomain();
        domain.setName("集成测试分类");
        domain.setCode(code);
        domain.setLifecycleStatus(lifecycleStatus);
        domain.setAccessPolicy(accessPolicy);
        return domainRepository.saveAndFlush(domain);
    }

    private void grantRead(CatalogDomain domain, String username) {
        AssetGrant grant = new AssetGrant();
        grant.setAssetType("CATALOG_DOMAIN");
        grant.setAssetId(domain.getId().toString());
        grant.setGranteeType("USER");
        grant.setGranteeId(username);
        grant.setPermission("READ");
        grant.setGrantedBy("integration-test");
        grantRepository.saveAndFlush(grant);
    }

    private static java.util.Set<UUID> flattenIds(java.util.List<java.util.Map<String, Object>> tree) {
        java.util.Set<UUID> ids = new java.util.LinkedHashSet<>();
        for (java.util.Map<String, Object> node : tree) {
            ids.add((UUID) node.get("id"));
            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> children =
                (java.util.List<java.util.Map<String, Object>>) node.get("children");
            ids.addAll(flattenIds(children));
        }
        return ids;
    }

    private static CatalogDomainResource.CatalogDomainListItem singleListItem(java.util.Map<String, Object> page) {
        return (CatalogDomainResource.CatalogDomainListItem) ((java.util.List<?>) page.get("content")).get(0);
    }

    private static java.util.Map<String, Object> findNode(java.util.List<java.util.Map<String, Object>> tree, UUID id) {
        for (java.util.Map<String, Object> node : tree) {
            if (id.equals(node.get("id"))) {
                return node;
            }
            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> children =
                (java.util.List<java.util.Map<String, Object>>) node.get("children");
            java.util.Map<String, Object> match = findNode(children, id);
            if (match != null) {
                return match;
            }
        }
        return null;
    }
}
