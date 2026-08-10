package com.yuzhi.dts.admin.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.domain.PortalMenu;
import com.yuzhi.dts.admin.domain.PortalMenuVisibility;
import com.yuzhi.dts.admin.domain.SystemConfig;
import com.yuzhi.dts.admin.repository.PortalMenuRepository;
import com.yuzhi.dts.admin.repository.PortalMenuVisibilityRepository;
import com.yuzhi.dts.admin.repository.SystemConfigRepository;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PortalMenuSeedDefaultsContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void roleMenuDefaultsDoNotBindAnyRoles() throws Exception {
        ClassPathResource resource = new ClassPathResource("config/data/role-menu-defaults.json");
        assertTrue(resource.exists(), "role-menu-defaults.json must exist");

        List<Map<String, Object>> rules = objectMapper.readValue(
            resource.getInputStream(),
            new TypeReference<List<Map<String, Object>>>() {}
        );

        assertFalse(rules.isEmpty(), "menu default rules should still document onsite-bindable menu entries");
        for (Map<String, Object> rule : rules) {
            Object requiredRoles = rule.get("requiredRoles");
            assertNotNull(requiredRoles, () -> "requiredRoles must be explicit for " + rule.get("code"));
            assertInstanceOf(List.class, requiredRoles, () -> "requiredRoles must be a list for " + rule.get("code"));
            assertTrue(((List<?>) requiredRoles).isEmpty(), () -> "seed menu must not bind roles for " + rule.get("code"));
        }
    }

    @Test
    void portalMenuSeedPlacesAnalyticsUnderDataAnalysisServicesWithoutChangingRoleDefaults() throws Exception {
        ClassPathResource seedResource = new ClassPathResource("config/data/portal-menu-seed.json");
        assertTrue(seedResource.exists(), "portal-menu-seed.json must exist");

        Map<String, Object> seed = objectMapper.readValue(seedResource.getInputStream(), new TypeReference<Map<String, Object>>() {});
        List<Map<String, Object>> roots = listOfMaps(seed.get("portalNavSections"));
        assertFalse(roots.isEmpty(), "portal menu seed must define root sections");

        Map<String, Object> consumptionRoot = roots
            .stream()
            .filter(node -> "consumption".equals(node.get("key")))
            .findFirst()
            .orElse(null);
        assertNotNull(consumptionRoot, "数据分析与服务 root menu must exist");
        assertEquals("数据分析与服务", consumptionRoot.get("title"));

        Map<String, Object> screens = listOfMaps(consumptionRoot.get("children"))
            .stream()
            .filter(node -> "screens".equals(node.get("key")))
            .findFirst()
            .orElse(null);
        assertNotNull(screens, "数据大屏 must live under 数据分析与服务");
        assertEquals("screens", screens.get("path"));
        assertEquals("/bi/screens", screens.get("externalLink"));

        Map<String, Object> biAppsRoot = listOfMaps(consumptionRoot.get("children"))
            .stream()
            .filter(node -> "sys.nav.portal.businessIntelligenceApps".equals(node.get("titleKey")))
            .findFirst()
            .orElse(null);
        assertNotNull(biAppsRoot, "商业智能应用 menu must still exist under 数据分析与服务");
        assertFalse(
            containsTitleKey(listOfMaps(biAppsRoot.get("children")), "sys.nav.portal.biScreens"),
            "商业智能应用 subtree must not own 数据大屏"
        );

        ClassPathResource defaultsResource = new ClassPathResource("config/data/role-menu-defaults.json");
        List<Map<String, Object>> defaults = objectMapper.readValue(
            defaultsResource.getInputStream(),
            new TypeReference<List<Map<String, Object>>>() {}
        );
        Map<String, Object> screensDefault = defaults
            .stream()
            .filter(rule -> "sys.nav.portal.biScreens".equals(rule.get("code")))
            .findFirst()
            .orElse(null);
        assertNotNull(screensDefault, "数据大屏 role default entry must stay documented");
        assertEquals("/bi/screens", screensDefault.get("route"));
        assertTrue(((List<?>) screensDefault.get("requiredRoles")).isEmpty(), "数据大屏 seed must not add default role bindings");
    }

    @Test
    void dataGovernanceKeepsSeparateCollectionAndAssetManagementEntrypoints() throws Exception {
        ClassPathResource seedResource = new ClassPathResource("config/data/portal-menu-seed.json");
        assertTrue(seedResource.exists(), "portal-menu-seed.json must exist");

        Map<String, Object> seed = objectMapper.readValue(seedResource.getInputStream(), new TypeReference<Map<String, Object>>() {});
        List<Map<String, Object>> roots = listOfMaps(seed.get("portalNavSections"));
        Map<String, Object> resourceRoot = roots.stream().filter(node -> "resource".equals(node.get("key"))).findFirst().orElse(null);
        Map<String, Object> governanceRoot = roots.stream().filter(node -> "governance".equals(node.get("key"))).findFirst().orElse(null);
        assertNotNull(resourceRoot, "数据集成 root menu must exist");
        assertNotNull(governanceRoot, "数据治理 root menu must exist");

        assertTrue(
            listOfMaps(resourceRoot.get("children")).stream().noneMatch(node -> "metadata".equals(node.get("key"))),
            "数据源结构采集 must be absorbed by the unified access workspace instead of remaining a top-level entry"
        );

        Map<String, Object> assetsGroup = listOfMaps(governanceRoot.get("children"))
            .stream()
            .filter(node -> "assets".equals(node.get("key")))
            .findFirst()
            .orElse(null);
        assertNotNull(assetsGroup, "数据治理 must expose 数据地图与资产 group");

        Map<String, Object> metadataManagement = listOfMaps(assetsGroup.get("children"))
            .stream()
            .filter(node -> "metadata-management".equals(node.get("key")))
            .findFirst()
            .orElse(null);
        assertNotNull(metadataManagement, "数据资产 must expose 元数据管理");
        assertEquals("sys.nav.portal.dataPortalMetadataManagement", metadataManagement.get("titleKey"));
        assertEquals("元数据管理", metadataManagement.get("title"));
        assertEquals("/catalog/metadata-management", metadataManagement.get("externalLink"));

        ClassPathResource defaultsResource = new ClassPathResource("config/data/role-menu-defaults.json");
        List<Map<String, Object>> defaults = objectMapper.readValue(
            defaultsResource.getInputStream(),
            new TypeReference<List<Map<String, Object>>>() {}
        );
        Map<String, Object> managementDefault = defaults
            .stream()
            .filter(rule -> "sys.nav.portal.dataPortalMetadataManagement".equals(rule.get("code")))
            .findFirst()
            .orElse(null);
        assertNotNull(managementDefault, "元数据管理 role default entry must stay documented");
        assertEquals("/catalog/metadata-management", managementDefault.get("route"));
        assertTrue(((List<?>) managementDefault.get("requiredRoles")).isEmpty(), "元数据管理 seed must not add default role bindings");
    }

    @Test
    void dataIntegrationUsesUnifiedAccessWorkspaceTree() throws Exception {
        ClassPathResource seedResource = new ClassPathResource("config/data/portal-menu-seed.json");
        Map<String, Object> seed = objectMapper.readValue(seedResource.getInputStream(), new TypeReference<Map<String, Object>>() {});
        List<Map<String, Object>> roots = listOfMaps(seed.get("portalNavSections"));
        Map<String, Object> resourceRoot = roots.stream().filter(node -> "resource".equals(node.get("key"))).findFirst().orElse(null);
        assertNotNull(resourceRoot, "数据集成 root menu must exist");

        List<Map<String, Object>> children = listOfMaps(resourceRoot.get("children"));
        assertEquals(
            List.of("accessOverview", "databaseAccess", "apiAccess", "fileAccess", "accessDefaults", "runtime"),
            children.stream().map(node -> String.valueOf(node.get("key"))).toList()
        );
        assertEquals("/foundation/data-sources", children.get(0).get("externalLink"));
        assertEquals("/foundation/data-sources/database", children.get(1).get("externalLink"));
        assertEquals("/foundation/data-sources/api", children.get(2).get("externalLink"));
        assertEquals("/foundation/data-sources/files", children.get(3).get("externalLink"));
        assertEquals("/foundation/data-sources/defaults", children.get(4).get("externalLink"));
        assertEquals(
            List.of("connectors", "jdbcDrivers"),
            listOfMaps(children.get(5).get("children")).stream().map(node -> String.valueOf(node.get("key"))).toList()
        );

        String resourceJson = objectMapper.writeValueAsString(resourceRoot);
        assertFalse(resourceJson.contains("/explore/etl/transform"), "legacy ingestion menu must be removed");
        assertFalse(resourceJson.contains("/foundation/access-changes"), "legacy access-change menu must be removed");
        assertFalse(resourceJson.contains("/catalog/metadata"), "legacy source-collection menu must be removed");

        ClassPathResource defaultsResource = new ClassPathResource("config/data/role-menu-defaults.json");
        List<Map<String, Object>> defaults = objectMapper.readValue(
            defaultsResource.getInputStream(),
            new TypeReference<List<Map<String, Object>>>() {}
        );
        Map<String, String> routesByCode = defaults
            .stream()
            .collect(java.util.stream.Collectors.toMap(rule -> String.valueOf(rule.get("code")), rule -> String.valueOf(rule.get("route"))));
        assertEquals("/foundation/data-sources", routesByCode.get("sys.nav.portal.resourceAccessOverview"));
        assertEquals("/foundation/data-sources/database", routesByCode.get("sys.nav.portal.resourceDatabaseAccess"));
        assertEquals("/foundation/data-sources/api", routesByCode.get("sys.nav.portal.resourceApiAccess"));
        assertEquals("/foundation/data-sources/files", routesByCode.get("sys.nav.portal.resourceFileAccess"));
        assertEquals("/foundation/data-sources/defaults", routesByCode.get("sys.nav.portal.resourceAccessDefaults"));
        assertFalse(routesByCode.containsKey("sys.nav.portal.resourceIngestion"));
        assertFalse(routesByCode.containsKey("sys.nav.portal.resourceChanges"));
        assertFalse(routesByCode.containsKey("sys.nav.portal.resourceMetadata"));
    }

    @Test
    void dataIntegrationAccessComponentsResolveToCanonicalWorkspacePages() throws Exception {
        PortalMenuService service = new PortalMenuService(null, null, null, objectMapper, noOpTransactionManager());
        Method resolveComponent = PortalMenuService.class.getDeclaredMethod("resolveComponent", String.class);
        resolveComponent.setAccessible(true);

        assertEquals(
            "/pages/foundation/access/AccessWorkspacePage",
            resolveComponent.invoke(service, "resource.accessOverview")
        );
        assertEquals(
            "/pages/foundation/access/AccessWorkspacePage",
            resolveComponent.invoke(service, "resource.databaseAccess")
        );
        assertEquals(
            "/pages/foundation/access/AccessWorkspacePage",
            resolveComponent.invoke(service, "resource.apiAccess")
        );
        assertEquals(
            "/pages/foundation/access/AccessWorkspacePage",
            resolveComponent.invoke(service, "resource.fileAccess")
        );
        assertEquals(
            "/pages/foundation/access/AccessDefaultsPage",
            resolveComponent.invoke(service, "resource.accessDefaults")
        );
        assertEquals(
            "/pages/foundation/ConnectorRegistryPage",
            resolveComponent.invoke(service, "resource.runtime.connectors")
        );
        assertEquals(
            "/pages/foundation/JdbcDriversPage",
            resolveComponent.invoke(service, "resource.runtime.jdbcDrivers")
        );
    }

    @Test
    void dataIntegrationAccessMigrationPreservesIdsBindingsAndCustomMenus() throws Exception {
        String changelogFile = "20260731-04_data_integration_access_menu_convergence.xml";
        ClassPathResource master = new ClassPathResource("config/liquibase/master.xml");
        assertTrue(master.getContentAsString(StandardCharsets.UTF_8).contains(changelogFile));

        ClassPathResource changelog = new ClassPathResource("config/liquibase/changelog/" + changelogFile);
        assertTrue(changelog.exists(), "data-integration access convergence changelog must exist");
        String xml = changelog.getContentAsString(StandardCharsets.UTF_8);
        String forwardSql = xml.substring(0, xml.indexOf("<rollback>")).toLowerCase(Locale.ROOT);
        String overviewVisibilitySql = sqlBlock(
            xml,
            "-- Overview receives only the direct Sources, Ingestion, Metadata, and Changes triples.",
            "-- If seed upsert already created a second overview"
        );
        String accessModeVisibilitySql = sqlBlock(
            xml,
            "-- Database/API/file inherit only Sources + Ingestion. Defaults never receives historical bindings.",
            "SELECT id, created_by = 'data-integration-access-menu-convergence'\n" +
            "                  INTO runtime_id, runtime_was_created"
        );
        String runtimeVisibilitySql = sqlBlock(
            xml,
            "-- Connectors and JDBC keep only their own historical triples and their canonical row ids.",
            "-- Retire only enabled legacy access rows."
        );
        String rollbackSql = xml.substring(xml.indexOf("<rollback>")).toLowerCase(Locale.ROOT);

        assertTrue(
            xml.contains("access_overview_id := COALESCE(legacy_sources_id, existing_overview_id)"),
            "legacy Sources must be preferred so its menu id survives"
        );
        assertTrue(xml.contains("WHERE id = access_overview_id"), "Sources must be updated in place");
        assertTrue(xml.contains("CASE WHEN parent_id = resource_id THEN 0 ELSE 1 END"));
        assertTrue(xml.contains("SET parent_id = runtime_id"), "legacy runtime leaves must be reparented in place");
        assertTrue(xml.contains("portal_menu_visibility_seq"));
        assertTrue(xml.contains("target.data_level IS NOT DISTINCT FROM source.data_level"));
        assertTrue(xml.contains("portal.menu.seed.hash"), "upgraded installations must not enter the destructive unseeded path");
        assertFalse(forwardSql.contains("delete from portal_menu_visibility"), "forward migration must preserve existing role bindings");
        assertFalse(forwardSql.contains("delete from portal_menu "), "system and custom menu rows must stay recoverable");
        assertTrue(
            forwardSql.contains("metadata::jsonb ->> 'key' = 'resource'"),
            "root resolution must use the exact seed metadata key"
        );
        assertTrue(
            forwardSql.contains("raise exception 'data-integration menu root metadata key resource is missing'"),
            "a populated tree with no canonical resource root must fail closed"
        );
        assertFalse(forwardSql.contains("sys.nav.portal.resourcecenter"), "legacy title guesses must not select the root");
        assertFalse(forwardSql.contains("metadata like"), "menu identity must use exact JSON metadata values");

        assertTrue(overviewVisibilitySql.contains("source_menu_ids || ingestion_menu_ids || metadata_menu_ids || changes_menu_ids"));
        assertFalse(overviewVisibilitySql.contains("resource_id"), "root visibility must not fan out to overview");
        assertTrue(
            accessModeVisibilitySql.contains("visibility.menu_id = ANY(source_menu_ids || ingestion_menu_ids)"),
            "database/API/file must map only Sources and Ingestion triples"
        );
        assertTrue(
            accessModeVisibilitySql.contains("IF desired.menu_key <> 'accessDefaults' THEN"),
            "Defaults must receive no historical visibility mapping"
        );
        assertFalse(accessModeVisibilitySql.contains("metadata_menu_ids"));
        assertFalse(accessModeVisibilitySql.contains("changes_menu_ids"));
        assertFalse(accessModeVisibilitySql.contains("resource_id)"), "parent visibility must not fan out to access modes");
        assertTrue(runtimeVisibilitySql.contains("THEN connector_menu_ids ELSE jdbc_menu_ids END"));
        assertFalse(runtimeVisibilitySql.contains("source_menu_ids"));
        assertFalse(runtimeVisibilitySql.contains("ingestion_menu_ids"));
        assertFalse(runtimeVisibilitySql.contains("resource_id, access_overview_id"));
        assertTrue(
            xml.contains("SELECT DISTINCT visibility.role_code, visibility.permission_code, visibility.data_level"),
            "visibility mappings must preserve complete source triples"
        );

        assertTrue(forwardSql.contains("and deleted = false"), "only enabled legacy rows may be retired or mapped");
        assertTrue(forwardSql.contains("data-integration-access-menu-convergence-retired-enabled"));
        assertTrue(
            forwardSql.contains("data-integration-access-menu-convergence-created"),
            "rows introduced and enabled by the migration must have a distinct rollback marker"
        );
        assertFalse(forwardSql.contains("migrated-sources-enabled"), "pre-deleted Sources must never be selected or revived");
        assertFalse(forwardSql.contains("migrated-direct-enabled"), "pre-deleted runtime leaves must never be selected or revived");
        assertTrue(
            forwardSql.contains("portal.menu.access.convergence.snapshot.20260731-04") &&
            forwardSql.contains("jsonb_build_array(to_jsonb(snapshot_menu))"),
            "forward migration must persist exact pre-update menu snapshots"
        );
        assertTrue(
            rollbackSql.contains("lateral jsonb_array_elements(config.cfg_value::jsonb) snapshot_item") &&
            rollbackSql.contains("component = snapshots.snapshot_item ->> 'component'") &&
            rollbackSql.contains("metadata = snapshots.snapshot_item ->> 'metadata'") &&
            rollbackSql.contains("parent_id = nullif(snapshots.snapshot_item ->> 'parent_id', '')::bigint") &&
            rollbackSql.contains("last_modified_date = nullif(snapshots.snapshot_item ->> 'last_modified_date', '')::timestamp"),
            "rollback must restore every overwritten field from the persisted snapshot"
        );
        assertTrue(
            rollbackSql.contains("data-integration menu convergence rollback snapshot is missing"),
            "rollback must fail closed rather than guess when the snapshot is unavailable"
        );
        assertTrue(
            rollbackSql.contains("delete from system_config") && rollbackSql.contains("where cfg_key = snapshot_key"),
            "rollback must consume its migration-owned snapshot so reapply is stable"
        );
        assertFalse(
            forwardSql.contains("metadata like '%\"key\":\"metadata\"%'") ||
            forwardSql.contains("metadata like '%\"entrykey\":\"metadata\"%'"),
            "legacy retirement must match exact system title keys instead of broad customer-defined keys"
        );
        assertTrue(
            xml.contains("WHERE created_by = 'data-integration-access-menu-convergence'"),
            "rollback may remove only visibility rows created by this migration"
        );
    }

    @Test
    void dataModelingSeedUsesPrototypeRootHierarchyOutsideStudio() throws Exception {
        ClassPathResource seedResource = new ClassPathResource("config/data/portal-menu-seed.json");
        assertTrue(seedResource.exists(), "portal-menu-seed.json must exist");

        Map<String, Object> seed = objectMapper.readValue(seedResource.getInputStream(), new TypeReference<Map<String, Object>>() {});
        List<Map<String, Object>> roots = listOfMaps(seed.get("portalNavSections"));
        Map<String, Object> studioRoot = roots.stream().filter(node -> "studio".equals(node.get("key"))).findFirst().orElse(null);
        Map<String, Object> modeling = roots.stream().filter(node -> "modeling".equals(node.get("key"))).findFirst().orElse(null);
        assertNotNull(studioRoot, "数据开发与运维 root menu must exist");
        assertEquals("数据开发与运维", studioRoot.get("title"));
        assertFalse(
            listOfMaps(studioRoot.get("children")).stream().anyMatch(node -> "modeling".equals(node.get("key"))),
            "数据开发与运维 must not retain the legacy modeling subtree"
        );
        assertNotNull(modeling, "数据建模 must be a root menu");
        assertEquals("数据建模", modeling.get("title"));

        List<Map<String, Object>> modelingChildren = listOfMaps(modeling.get("children"));
        assertEquals(7, modelingChildren.size(), "modeling must expose one overview and six capability groups");
        assertEquals("modeling-home-workspace", modelingChildren.get(0).get("key"));
        assertEquals("建模概览", modelingChildren.get(0).get("title"));
        assertEquals("/data-modeling/home/workspace", modelingChildren.get(0).get("externalLink"));
        assertEquals("planning-system", modelingChildren.get(1).get("key"), "建模策略 should follow 建模概览");
        assertEquals("standards", modelingChildren.get(2).get("key"), "数据标准 should follow 建模策略");
        assertEquals("dimensional-modeling", modelingChildren.get(3).get("key"), "维度建模 should follow 数据标准");
        assertEquals("data-metrics", modelingChildren.get(4).get("key"), "数据指标 should follow 维度建模");
        assertEquals("modeling-tools", modelingChildren.get(5).get("key"), "通用工具 should follow 数据指标");
        assertEquals("modeling-graphs", modelingChildren.get(6).get("key"), "关系图 should be the final prototype group");
        assertFalse(
            modelingChildren.stream().anyMatch(node -> "modeling-home".equals(node.get("key"))),
            "the redundant modeling home group must be removed"
        );

        Map<String, Object> planningSystem = modelingChildren
            .stream()
            .filter(node -> "planning-system".equals(node.get("key")))
            .findFirst()
            .orElse(null);
        assertNotNull(planningSystem, "数据建模 root must expose modeling strategy directly");
        assertEquals("建模策略", planningSystem.get("title"));
        assertFalse(modelingChildren.stream().anyMatch(node -> "warehouse-planning".equals(node.get("key"))));

        Map<String, Object> dimensionalModeling = modelingChildren
            .stream()
            .filter(node -> "dimensional-modeling".equals(node.get("key")))
            .findFirst()
            .orElse(null);
        assertNotNull(dimensionalModeling, "数据建模 root must expose 维度建模");
        List<Map<String, Object>> dimensionChildren = listOfMaps(dimensionalModeling.get("children"));
        assertEquals(2, dimensionChildren.size(), "维度建模 must expose model workbench and reverse modeling");
        assertEquals("/data-modeling/dimensions/workbench", dimensionChildren.get(0).get("externalLink"));
        assertEquals("/data-modeling/dimensions/reverse", dimensionChildren.get(1).get("externalLink"));

        Map<String, Object> dataStudio = listOfMaps(studioRoot.get("children"))
            .stream()
            .filter(node -> "data-studio".equals(node.get("key")))
            .findFirst()
            .orElse(null);
        assertNotNull(dataStudio, "数据开发与运维 must expose Data Studio");
        assertTrue(containsTitleKey(listOfMaps(dataStudio.get("children")), "sys.nav.portal.studioScripts"), "script development must stay available");
        assertTrue(
            containsTitleKey(listOfMaps(dataStudio.get("children")), "sys.nav.portal.studioOrchestration"),
            "orchestration must stay available"
        );

        ClassPathResource defaultsResource = new ClassPathResource("config/data/role-menu-defaults.json");
        List<Map<String, Object>> defaults = objectMapper.readValue(
            defaultsResource.getInputStream(),
            new TypeReference<List<Map<String, Object>>>() {}
        );
        assertEquals(
            26,
            defaults.stream().filter(rule -> String.valueOf(rule.get("route")).startsWith("/data-modeling/")).count(),
            "role defaults must document every prototype leaf without granting roles"
        );
        assertFalse(
            defaults
                .stream()
                .anyMatch(rule ->
                    List.of("sys.nav.portal.modelingHomeRecent", "sys.nav.portal.modelingHomeTasks").contains(rule.get("code"))
                ),
            "role defaults must not keep the retired recent/tasks menu entries"
        );
        assertFalse(
            defaults
                .stream()
                .anyMatch(rule ->
                    List
                        .of(
                            "sys.nav.portal.warehousePlans",
                            "sys.nav.portal.studioBusinessProcesses",
                            "sys.nav.portal.studioSemanticObjects",
                            "sys.nav.portal.studioSemanticModels",
                            "sys.nav.portal.studioSqlModeling",
                            "sys.nav.portal.studioMetricWorkbench"
                        )
                        .contains(rule.get("code"))
                ),
            "role defaults must not keep retired modeling menu entries"
        );
        assertTrue(
            defaults
                .stream()
                .filter(rule -> String.valueOf(rule.get("route")).startsWith("/data-modeling/"))
                .allMatch(rule -> ((List<?>) rule.get("requiredRoles")).isEmpty()),
            "Sprint-80 menu documentation must not create implicit role grants"
        );
    }

    @Test
    void sprint80ModelingMenuMigrationPreservesRootVisibilityAndSoftDeletesLegacyChildren() throws Exception {
        String changelogFile = "20260731-02_sprint80_prototype_modeling_menu.xml";
        ClassPathResource master = new ClassPathResource("config/liquibase/master.xml");
        assertTrue(master.getContentAsString(StandardCharsets.UTF_8).contains(changelogFile));

        ClassPathResource changelog = new ClassPathResource("config/liquibase/changelog/" + changelogFile);
        assertTrue(changelog.exists(), "Sprint-80 modeling menu migration changelog must exist");
        String xml = changelog.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(xml.contains("parent_id = NULL"), "modeling must be promoted to a root node");
        assertTrue(xml.contains("/data-modeling/home/workspace"));
        assertTrue(xml.contains("/data-modeling/graphs/metrics"));
        assertTrue(xml.contains("WITH RECURSIVE descendants"));
        assertTrue(xml.contains("deleted = TRUE"));
        String forwardSql = xml.substring(0, xml.indexOf("<rollback>")).toLowerCase(Locale.ROOT);
        assertFalse(forwardSql.contains("delete from portal_menu_visibility"), "forward migration must preserve visibility rows");
        assertTrue(xml.contains("target_menu_id"), "PL/pgSQL variables must not shadow the menu_id column");
        assertTrue(xml.contains("source.menu_id = parent_menu_id"), "new descendants must inherit only from their direct parent");
        assertTrue(xml.contains("target.data_level IS NOT DISTINCT FROM source.data_level"));
        assertTrue(
            xml.contains("created_by = 'sprint80-prototype-modeling-ui'"),
            "rollback must target only rows created by Sprint-80"
        );
        assertTrue(
            xml.toLowerCase(Locale.ROOT).contains("delete from portal_menu_visibility"),
            "rollback must remove only visibility rows created by Sprint-80"
        );
        assertFalse(xml.toLowerCase(Locale.ROOT).contains("delete from portal_menu "));
    }

    @Test
    void sprint80MenuReviewPromotesOverviewAndPreservesVisibilityBindings() throws Exception {
        String changelogFile = "20260731-03_sprint80_modeling_menu_review.xml";
        ClassPathResource master = new ClassPathResource("config/liquibase/master.xml");
        assertTrue(master.getContentAsString(StandardCharsets.UTF_8).contains(changelogFile));

        ClassPathResource changelog = new ClassPathResource("config/liquibase/changelog/" + changelogFile);
        assertTrue(changelog.exists(), "Sprint-80 menu-review changelog must exist");
        String xml = changelog.getContentAsString(StandardCharsets.UTF_8);
        String forwardSql = xml.substring(0, xml.indexOf("<rollback>")).toLowerCase(Locale.ROOT);

        assertTrue(xml.contains("sys.nav.portal.modelingHomeWorkspace"));
        assertTrue(xml.contains("requires the existing modeling-home group"), "the review migration must fail closed on an invalid base tree");
        assertTrue(xml.contains("parent_id IN (home_id, modeling_id)"), "overview lookup must stay inside the modeling subtree");
        assertTrue(xml.contains("'title', '建模概览'"));
        assertTrue(xml.contains("parent_id = modeling_id"), "overview must be promoted without replacing its row");
        assertTrue(xml.contains("sys.nav.portal.modelingHomeRecent"));
        assertTrue(xml.contains("sys.nav.portal.modelingHomeTasks"));
        assertTrue(xml.contains("'title', '规划参数配置'"));
        assertTrue(xml.contains("/data-modeling/home/workspace"), "the stable overview route must be preserved");
        assertFalse(forwardSql.contains("insert into portal_menu"), "the review migration must preserve existing menu ids");
        assertFalse(forwardSql.contains("insert into portal_menu_visibility"), "the review migration must not add visibility bindings");
        assertFalse(forwardSql.contains("update portal_menu_visibility"), "the review migration must not rewrite visibility bindings");
        assertFalse(xml.toLowerCase(Locale.ROOT).contains("delete from portal_menu "));
        assertFalse(xml.toLowerCase(Locale.ROOT).contains("delete from portal_menu_visibility"));
    }

    @Test
    void genericModelingWorkbenchMigrationPreservesVisibilityBindings() throws Exception {
        String changelogFile = "20260717-01_generic_modeling_workbench_menu.xml";
        ClassPathResource master = new ClassPathResource("config/liquibase/master.xml");
        String masterXml = master.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(masterXml.contains(changelogFile));

        ClassPathResource changelog = new ClassPathResource("config/liquibase/changelog/" + changelogFile);
        assertTrue(changelog.exists(), "generic modeling workbench migration changelog must exist");
        String xml = changelog.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(xml.contains("sys.nav.portal.studioBusinessProcesses"));
        assertTrue(xml.contains("/modeling/workbench"));
        assertFalse(xml.toLowerCase(Locale.ROOT).contains("delete from portal_menu_visibility"));
    }

    @Test
    void sprint67ModelingMenuMigrationSoftDeletesWithoutRemovingVisibilityBindings() throws Exception {
        String changelogFile = "20260719-01_sprint67_modeling_menu_convergence.xml";
        ClassPathResource master = new ClassPathResource("config/liquibase/master.xml");
        assertTrue(master.getContentAsString(StandardCharsets.UTF_8).contains(changelogFile));

        ClassPathResource changelog = new ClassPathResource("config/liquibase/changelog/" + changelogFile);
        assertTrue(changelog.exists(), "Sprint-67 menu convergence changelog must exist");
        String xml = changelog.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(xml.contains("deleted = TRUE"));
        assertTrue(xml.contains("高级建模（SQL/dbt）"));
        assertFalse(xml.toLowerCase(Locale.ROOT).contains("delete from portal_menu_visibility"));
        assertFalse(xml.toLowerCase(Locale.ROOT).contains("delete from portal_menu "));
    }

    @Test
    void sprint79ModelingMenuRestoreReactivatesOnlyConvergenceRowsAndPreservesBindings() throws Exception {
        String changelogFile = "20260731-01_sprint79_modeling_menu_restore.xml";
        ClassPathResource master = new ClassPathResource("config/liquibase/master.xml");
        assertTrue(master.getContentAsString(StandardCharsets.UTF_8).contains(changelogFile));

        ClassPathResource changelog = new ClassPathResource("config/liquibase/changelog/" + changelogFile);
        assertTrue(changelog.exists(), "Sprint-79 modeling menu restore changelog must exist");
        String xml = changelog.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(xml.contains("onFail=\"HALT\""));
        assertTrue(xml.contains("columnName=\"last_modified_date\""));
        assertTrue(xml.contains("deleted = FALSE"));
        assertTrue(xml.contains("last_modified_by = 'sprint79-menu-convergence'"));
        assertTrue(xml.contains("last_modified_by = 'sprint79-menu-restore'"));
        assertFalse(xml.toLowerCase(Locale.ROOT).contains("delete from portal_menu_visibility"));
        assertFalse(xml.toLowerCase(Locale.ROOT).contains("delete from portal_menu "));
    }

    @Test
    void dataScreenRootMigrationPreservesVisibilityBindings() throws Exception {
        String changelogFile = "20260525-01_portal_menu_data_screen_root.xml";
        ClassPathResource master = new ClassPathResource("config/liquibase/master.xml");
        String masterXml = master.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(masterXml.contains(changelogFile), "master.xml must include the data screen root migration");

        ClassPathResource changelog = new ClassPathResource("config/liquibase/changelog/" + changelogFile);
        assertTrue(changelog.exists(), "data screen root migration changelog must exist");

        String changelogXml = changelog.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(changelogXml.contains("sys.nav.portal.biScreens"));
        assertTrue(changelogXml.contains("parent_id = NULL"));
        assertTrue(changelogXml.contains("portal.menu.seed.hash"));

        String lowerXml = changelogXml.toLowerCase(Locale.ROOT);
        assertFalse(lowerXml.contains("delete from portal_menu_visibility"));
        assertFalse(lowerXml.contains("delete tablename=\"portal_menu_visibility\""));
    }

    @Test
    void menuWithoutExplicitVisibilityIsNotVisibleToOperatorAdmin() throws Exception {
        PortalMenuService service = new PortalMenuService(null, null, null, objectMapper, noOpTransactionManager());
        PortalMenu menu = new PortalMenu();
        menu.setMetadata("{}");
        menu.setChildren(new ArrayList<>());
        menu.setVisibilities(new ArrayList<>());

        Method defaultVisibilities = PortalMenuService.class.getDeclaredMethod("defaultVisibilities", PortalMenu.class);
        defaultVisibilities.setAccessible(true);
        assertTrue(((List<?>) defaultVisibilities.invoke(service, menu)).isEmpty());

        Method isMenuVisible = PortalMenuService.class.getDeclaredMethod(
            "isMenuVisible",
            PortalMenu.class,
            Set.class,
            Set.class,
            String.class
        );
        isMenuVisible.setAccessible(true);

        boolean visible = (boolean) isMenuVisible.invoke(service, menu, Set.of("ROLE_OP_ADMIN"), Set.of(), null);

        assertFalse(visible, "unbound seed menus must stay hidden until roles are bound onsite");
    }

    @Test
    void legacyBusinessConsumptionSeedMenuIsHiddenAfterDataManagementWorkbenchMigration() throws Exception {
        PortalMenuService service = new PortalMenuService(null, null, null, objectMapper, noOpTransactionManager());
        PortalMenu menu = menuWithVisibility(
            100L,
            "{\"key\":\"consumption\",\"sectionKey\":\"services\",\"entryKey\":\"consumption\",\"titleKey\":\"sys.nav.portal.servicesConsumption\"}"
        );
        menu.setPath("services/consumption");
        menu.setChildren(new ArrayList<>());

        Method filterMenu = PortalMenuService.class.getDeclaredMethod(
            "filterMenu",
            PortalMenu.class,
            Set.class,
            Set.class,
            String.class
        );
        filterMenu.setAccessible(true);

        Object filtered = filterMenu.invoke(service, menu, Set.of("ROLE_OP_ADMIN"), Set.of(), null);

        assertEquals(null, filtered, "legacy services/consumption seed menu must be hidden while the route stays compatible");
    }

    @Test
    void seedUpsertedDataManagementWorkbenchChildKeepsParentVisibilityAndRouteComponent() throws Exception {
        PortalMenuRepository menuRepository = mock(PortalMenuRepository.class);
        PortalMenuService service = new PortalMenuService(
            menuRepository,
            null,
            mock(SystemConfigRepository.class),
            objectMapper,
            noOpTransactionManager()
        );
        PortalMenu services = menuWithVisibility(10L, "{\"key\":\"workbench\",\"sectionKey\":\"workbench\"}");
        services.setPath("workbench");
        when(menuRepository.findByParentIdOrderBySortOrderAscIdAsc(10L)).thenReturn(List.of());

        Method ensureChildrenFromSeed = PortalMenuService.class.getDeclaredMethod(
            "ensureChildrenFromSeed",
            PortalMenu.class,
            List.class,
            int.class,
            String.class,
            String.class
        );
        ensureChildrenFromSeed.setAccessible(true);
        ensureChildrenFromSeed.invoke(
            service,
            services,
            List.of(
                menuNode(
                    "data-management",
                    "data-management",
                    "monitor",
                    "sys.nav.portal.workbenchDataManagement",
                    "数据管理工作台",
                    "/workbench/data-management",
                    List.of()
                )
            ),
            1,
            "workbench",
            "workbench"
        );

        ArgumentCaptor<PortalMenu> menuCaptor = ArgumentCaptor.forClass(PortalMenu.class);
        verify(menuRepository).save(menuCaptor.capture());
        PortalMenu consumption = menuCaptor.getValue();

        assertEquals("workbench/data-management", consumption.getPath());
        assertEquals("/pages/workbench/DataManagementWorkbenchPage", consumption.getComponent());
        assertEquals(1, consumption.getVisibilities().size(), "new seed child must inherit onsite parent visibility");
        assertEquals("ROLE_OP_ADMIN", consumption.getVisibilities().get(0).getRoleCode());
    }

    @Test
    void accessDefaultsStaysUnboundAcrossRepeatedSeedSynchronization() throws Exception {
        PortalMenuRepository menuRepository = mock(PortalMenuRepository.class);
        PortalMenuService service = new PortalMenuService(
            menuRepository,
            null,
            mock(SystemConfigRepository.class),
            objectMapper,
            noOpTransactionManager()
        );
        PortalMenu resource = menuWithVisibility(
            20L,
            "{\"key\":\"resource\",\"sectionKey\":\"resource\",\"titleKey\":\"sys.nav.portal.dataIntegration\"}"
        );
        resource.setName("sys.nav.portal.dataIntegration");
        resource.setPath("resource");

        PortalMenu accessDefaults = new PortalMenu();
        accessDefaults.setId(21L);
        accessDefaults.setName("customer.alias.access-defaults");
        accessDefaults.setPath("resource/access-defaults");
        accessDefaults.setMetadata(
            "{\"key\":\"accessDefaults\",\"sectionKey\":\"resource\",\"entryKey\":\"accessDefaults\",\"titleKey\":\"sys.nav.portal.resourceAccessDefaults\"}"
        );
        accessDefaults.setParent(resource);
        accessDefaults.setChildren(new ArrayList<>());
        accessDefaults.setVisibilities(new ArrayList<>());
        PortalMenu sources = menuWithVisibility(
            22L,
            "{\"key\":\"sources\",\"sectionKey\":\"resource\",\"entryKey\":\"sources\"}"
        );
        sources.setName("sys.nav.portal.resourceSources");
        sources.setParent(resource);
        sources.getVisibilities().get(0).setRoleCode("ROLE_SOURCES");
        when(menuRepository.findByParentIdOrderBySortOrderAscIdAsc(20L)).thenReturn(List.of(accessDefaults, sources));

        Method ensureChildrenFromSeed = PortalMenuService.class.getDeclaredMethod(
            "ensureChildrenFromSeed",
            PortalMenu.class,
            List.class,
            int.class,
            String.class,
            String.class
        );
        ensureChildrenFromSeed.setAccessible(true);
        Object defaultsSeed = menuNode(
            "accessDefaults",
            "access-defaults",
            "settings",
            "sys.nav.portal.resourceAccessDefaults",
            "默认策略",
            "/foundation/data-sources/defaults",
            List.of()
        );

        ensureChildrenFromSeed.invoke(service, resource, List.of(defaultsSeed), 1, "resource", "resource");
        ensureChildrenFromSeed.invoke(service, resource, List.of(defaultsSeed), 1, "resource", "resource");

        assertTrue(
            accessDefaults.getVisibilities().isEmpty(),
            "seed sync must not fan out parent or sibling visibility to accessDefaults"
        );
    }

    @Test
    void seedUpsertedDataManagementWorkbenchChildInheritsSiblingVisibilityWhenParentIsUnbound() throws Exception {
        PortalMenuRepository menuRepository = mock(PortalMenuRepository.class);
        PortalMenuService service = new PortalMenuService(
            menuRepository,
            null,
            mock(SystemConfigRepository.class),
            objectMapper,
            noOpTransactionManager()
        );
        PortalMenu services = new PortalMenu();
        services.setId(10L);
        services.setName("sys.nav.portal.workbench");
        services.setPath("workbench");
        services.setMetadata("{\"key\":\"workbench\",\"sectionKey\":\"workbench\"}");
        services.setVisibilities(new ArrayList<>());
        PortalMenu overview = menuWithVisibility(11L, "{\"key\":\"overview\",\"sectionKey\":\"workbench\",\"entryKey\":\"overview\"}");
        overview.setPath("workbench/overview");
        overview.setParent(services);
        overview.getVisibilities().get(0).setRoleCode("ROLE_INST_DATA_OWNER");
        PortalMenu todo = menuWithVisibility(12L, "{\"key\":\"todo\",\"sectionKey\":\"workbench\",\"entryKey\":\"todo\"}");
        todo.setPath("workbench/todo");
        todo.setParent(services);
        todo.getVisibilities().get(0).setRoleCode("ROLE_INST_LEADER");
        when(menuRepository.findByParentIdOrderBySortOrderAscIdAsc(10L)).thenReturn(List.of(overview, todo));

        Method ensureChildrenFromSeed = PortalMenuService.class.getDeclaredMethod(
            "ensureChildrenFromSeed",
            PortalMenu.class,
            List.class,
            int.class,
            String.class,
            String.class
        );
        ensureChildrenFromSeed.setAccessible(true);
        ensureChildrenFromSeed.invoke(
            service,
            services,
            List.of(
                menuNode(
                    "data-management",
                    "data-management",
                    "monitor",
                    "sys.nav.portal.workbenchDataManagement",
                    "数据管理工作台",
                    "/workbench/data-management",
                    List.of()
                )
            ),
            1,
            "workbench",
            "workbench"
        );

        ArgumentCaptor<PortalMenu> menuCaptor = ArgumentCaptor.forClass(PortalMenu.class);
        verify(menuRepository).save(menuCaptor.capture());
        PortalMenu consumption = menuCaptor.getValue();

        assertEquals("workbench/data-management", consumption.getPath());
        assertEquals(
            Set.of("ROLE_INST_DATA_OWNER", "ROLE_INST_LEADER"),
            consumption.getVisibilities().stream().map(PortalMenuVisibility::getRoleCode).collect(java.util.stream.Collectors.toSet())
        );
    }

    @Test
    void seedUpsertedChildDoesNotInheritCustomSiblingVisibility() throws Exception {
        PortalMenuRepository menuRepository = mock(PortalMenuRepository.class);
        PortalMenuService service = new PortalMenuService(
            menuRepository,
            null,
            mock(SystemConfigRepository.class),
            objectMapper,
            noOpTransactionManager()
        );
        PortalMenu services = new PortalMenu();
        services.setId(10L);
        services.setName("sys.nav.portal.workbench");
        services.setPath("workbench");
        services.setMetadata("{\"key\":\"workbench\",\"sectionKey\":\"workbench\"}");
        services.setVisibilities(new ArrayList<>());
        PortalMenu custom = menuWithVisibility(11L, "{\"title\":\"自定义链接\",\"sectionKey\":\"workbench\"}");
        custom.setName("custom.link.example");
        custom.setPath("workbench/link-example");
        custom.setParent(services);
        custom.getVisibilities().get(0).setRoleCode("ROLE_CUSTOM_ONLY");
        when(menuRepository.findByParentIdOrderBySortOrderAscIdAsc(10L)).thenReturn(List.of(custom));

        Method ensureChildrenFromSeed = PortalMenuService.class.getDeclaredMethod(
            "ensureChildrenFromSeed",
            PortalMenu.class,
            List.class,
            int.class,
            String.class,
            String.class
        );
        ensureChildrenFromSeed.setAccessible(true);
        ensureChildrenFromSeed.invoke(
            service,
            services,
            List.of(
                menuNode(
                    "data-management",
                    "data-management",
                    "monitor",
                    "sys.nav.portal.workbenchDataManagement",
                    "数据管理工作台",
                    "/workbench/data-management",
                    List.of()
                )
            ),
            1,
            "workbench",
            "workbench"
        );

        ArgumentCaptor<PortalMenu> menuCaptor = ArgumentCaptor.forClass(PortalMenu.class);
        verify(menuRepository).save(menuCaptor.capture());

        assertTrue(menuCaptor.getValue().getVisibilities().isEmpty());
    }

    @Test
    void seedHashMismatchDoesNotResetExistingMenuBindings() throws Exception {
        PortalMenuRepository menuRepository = mock(PortalMenuRepository.class);
        PortalMenuVisibilityRepository visibilityRepository = mock(PortalMenuVisibilityRepository.class);
        SystemConfigRepository configRepository = mock(SystemConfigRepository.class);
        PortalMenuService service = new PortalMenuService(
            menuRepository,
            visibilityRepository,
            configRepository,
            objectMapper,
            noOpTransactionManager()
        );
        SystemConfig config = new SystemConfig();
        config.setKey("portal.menu.seed.hash");
        config.setValue("old-seed-hash");
        PortalMenu services = menuWithVisibility(10L, "{\"key\":\"services\",\"sectionKey\":\"services\"}");
        services.setPath("services");
        services.setChildren(new ArrayList<>());
        when(configRepository.findByKey("portal.menu.seed.hash")).thenReturn(Optional.of(config));
        when(menuRepository.findByDeletedFalseAndParentIsNullOrderBySortOrderAscIdAsc()).thenReturn(List.of(services));
        when(menuRepository.findByParentIdOrderBySortOrderAscIdAsc(10L)).thenReturn(List.of());
        when(menuRepository.findAll()).thenReturn(List.of(services));

        Method ensureSeedMenus = PortalMenuService.class.getDeclaredMethod("ensureSeedMenus");
        ensureSeedMenus.setAccessible(true);
        ensureSeedMenus.invoke(service);

        verify(visibilityRepository, never()).deleteAllInBatch();
        verify(menuRepository, never()).deleteAllInBatch();
    }

    @Test
    void freshInstallMigrationClearsLegacySeedVisibilityBindingsBeforeSeedHashIsStored() throws Exception {
        ClassPathResource master = new ClassPathResource("config/liquibase/master.xml");
        String masterXml = master.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(masterXml.contains("20260519-01_portal_menu_seed_no_default_roles.xml"));

        ClassPathResource cleanup = new ClassPathResource(
            "config/liquibase/changelog/20260519-01_portal_menu_seed_no_default_roles.xml"
        );
        assertTrue(cleanup.exists(), "fresh-install cleanup changelog must exist");

        String cleanupXml = cleanup.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(cleanupXml.contains("DELETE FROM portal_menu_visibility"));
        assertTrue(cleanupXml.contains("portal.menu.seed.hash"));
    }

    @Test
    void clearSeedMenuRoleBindingsRemovesExistingVisibilityOnlyForSeedManagedMenus() {
        PortalMenuRepository menuRepository = mock(PortalMenuRepository.class);
        PortalMenuVisibilityRepository visibilityRepository = mock(PortalMenuVisibilityRepository.class);
        PortalMenuService service = new PortalMenuService(
            menuRepository,
            visibilityRepository,
            mock(SystemConfigRepository.class),
            objectMapper,
            noOpTransactionManager()
        );
        PortalMenu seedMenu = menuWithVisibility(1L, "{\"key\":\"workbench\",\"sectionKey\":\"workbench\"}");
        PortalMenu customMenu = menuWithVisibility(2L, "{\"key\":\"custom\",\"sectionKey\":\"custom\"}");
        PortalMenuVisibility seedVisibility = seedMenu.getVisibilities().get(0);
        PortalMenuVisibility customVisibility = customMenu.getVisibilities().get(0);
        when(menuRepository.findAll()).thenReturn(List.of(seedMenu, customMenu));

        service.clearSeedMenuRoleBindings();

        assertTrue(seedMenu.getVisibilities().isEmpty(), "seed menu bindings must be removed for onsite binding");
        assertFalse(customMenu.getVisibilities().isEmpty(), "custom menu bindings must not be removed by seed cleanup");
        verify(visibilityRepository).delete(seedVisibility);
        verify(visibilityRepository, never()).delete(customVisibility);
        verify(menuRepository).flush();
    }

    private Object menuNode(
        String key,
        String path,
        String icon,
        String titleKey,
        String title,
        String externalLink,
        List<?> children
    ) throws Exception {
        Class<?> menuNodeType = Class.forName("com.yuzhi.dts.admin.service.PortalMenuService$MenuNode");
        Constructor<?> constructor = menuNodeType.getDeclaredConstructor(
            String.class,
            String.class,
            String.class,
            String.class,
            String.class,
            String.class,
            List.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(key, path, icon, titleKey, title, externalLink, children);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return (List<Map<String, Object>>) (List<?>) list;
    }

    private boolean containsTitleKey(List<Map<String, Object>> nodes, String titleKey) {
        for (Map<String, Object> node : nodes) {
            if (titleKey.equals(node.get("titleKey"))) {
                return true;
            }
            if (containsTitleKey(listOfMaps(node.get("children")), titleKey)) {
                return true;
            }
        }
        return false;
    }

    private String sqlBlock(String sql, String startMarker, String endMarker) {
        int start = sql.indexOf(startMarker);
        int end = sql.indexOf(endMarker, start + startMarker.length());
        assertTrue(start >= 0, () -> "missing SQL contract marker: " + startMarker);
        assertTrue(end > start, () -> "missing SQL contract marker: " + endMarker);
        return sql.substring(start, end);
    }

    private PortalMenu menuWithVisibility(Long id, String metadata) {
        PortalMenu menu = new PortalMenu();
        menu.setId(id);
        menu.setName("menu-" + id);
        menu.setPath("menu-" + id);
        menu.setMetadata(metadata);
        PortalMenuVisibility visibility = new PortalMenuVisibility();
        visibility.setId(id);
        visibility.setRoleCode("ROLE_OP_ADMIN");
        menu.addVisibility(visibility);
        return menu;
    }

    private PlatformTransactionManager noOpTransactionManager() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {}

            @Override
            public void rollback(TransactionStatus status) {}
        };
    }
}
