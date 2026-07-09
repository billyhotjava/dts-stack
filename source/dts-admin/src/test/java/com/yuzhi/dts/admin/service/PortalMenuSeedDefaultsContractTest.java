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

        Map<String, Object> sourceStructure = listOfMaps(resourceRoot.get("children"))
            .stream()
            .filter(node -> "metadata".equals(node.get("key")))
            .findFirst()
            .orElse(null);
        assertNotNull(sourceStructure, "数据源结构采集 must stay in data integration");
        assertEquals("数据源结构采集", sourceStructure.get("title"));
        assertEquals("/catalog/metadata", sourceStructure.get("externalLink"));

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
    void dataModelingSeedFollowsPlanningStandardsDimensionMetricsOrder() throws Exception {
        ClassPathResource seedResource = new ClassPathResource("config/data/portal-menu-seed.json");
        assertTrue(seedResource.exists(), "portal-menu-seed.json must exist");

        Map<String, Object> seed = objectMapper.readValue(seedResource.getInputStream(), new TypeReference<Map<String, Object>>() {});
        List<Map<String, Object>> roots = listOfMaps(seed.get("portalNavSections"));
        Map<String, Object> studioRoot = roots.stream().filter(node -> "studio".equals(node.get("key"))).findFirst().orElse(null);
        assertNotNull(studioRoot, "数据开发与运维 root menu must exist");
        assertEquals("数据开发与运维", studioRoot.get("title"));

        Map<String, Object> modeling = listOfMaps(studioRoot.get("children"))
            .stream()
            .filter(node -> "modeling".equals(node.get("key")))
            .findFirst()
            .orElse(null);
        assertNotNull(modeling, "数据开发与运维 must expose 数据建模");

        List<Map<String, Object>> modelingChildren = listOfMaps(modeling.get("children"));
        assertEquals("warehouse-planning", modelingChildren.get(0).get("key"), "数仓规划 should be the first modeling entry");
        assertEquals("standards", modelingChildren.get(1).get("key"), "数据标准 should follow 数仓规划");
        assertEquals("dimensional-modeling", modelingChildren.get(2).get("key"), "维度建模 should follow 数据标准");
        assertEquals("data-metrics", modelingChildren.get(3).get("key"), "数据指标 should follow 维度建模");

        Map<String, Object> dimensionalModeling = modelingChildren
            .stream()
            .filter(node -> "dimensional-modeling".equals(node.get("key")))
            .findFirst()
            .orElse(null);
        assertNotNull(dimensionalModeling, "数据建模 must expose 维度建模");

        List<Map<String, Object>> dimensionChildren = listOfMaps(dimensionalModeling.get("children"));
        Map<String, Object> lowCode = dimensionChildren
            .stream()
            .filter(node -> "low-code-development".equals(node.get("key")))
            .findFirst()
            .orElse(null);
        assertNotNull(lowCode, "维度建模 must expose 低代码开发向导");
        assertEquals("sys.nav.portal.studioLowCodeDevelopment", lowCode.get("titleKey"));
        assertEquals("低代码开发向导", lowCode.get("title"));
        assertEquals("/studio/low-code-development", lowCode.get("externalLink"));
        assertTrue(containsTitleKey(dimensionChildren, "sys.nav.portal.studioSqlModeling"), "SQL modeling must stay available");

        Map<String, Object> dataMetrics = modelingChildren
            .stream()
            .filter(node -> "data-metrics".equals(node.get("key")))
            .findFirst()
            .orElse(null);
        assertNotNull(dataMetrics, "数据建模 must expose 数据指标");
        assertTrue(containsTitleKey(listOfMaps(dataMetrics.get("children")), "sys.nav.portal.studioMetricWorkbench"));
        assertFalse(containsTitleKey(dimensionChildren, "sys.nav.portal.studioMetricWorkbench"), "metric workbench must stay in 数据指标");

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
        Map<String, Object> lowCodeDefault = defaults
            .stream()
            .filter(rule -> "sys.nav.portal.studioLowCodeDevelopment".equals(rule.get("code")))
            .findFirst()
            .orElse(null);
        assertNotNull(lowCodeDefault, "低代码开发向导 role default entry must stay documented");
        assertEquals("/studio/low-code-development", lowCodeDefault.get("route"));
        assertTrue(((List<?>) lowCodeDefault.get("requiredRoles")).isEmpty(), "低代码开发向导 seed must not add default role bindings");
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
