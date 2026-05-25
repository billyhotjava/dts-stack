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
import com.yuzhi.dts.admin.repository.PortalMenuRepository;
import com.yuzhi.dts.admin.repository.PortalMenuVisibilityRepository;
import com.yuzhi.dts.admin.repository.SystemConfigRepository;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
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
    void portalMenuSeedPromotesDataScreensToRootWithoutChangingRoleDefaults() throws Exception {
        ClassPathResource seedResource = new ClassPathResource("config/data/portal-menu-seed.json");
        assertTrue(seedResource.exists(), "portal-menu-seed.json must exist");

        Map<String, Object> seed = objectMapper.readValue(seedResource.getInputStream(), new TypeReference<Map<String, Object>>() {});
        List<Map<String, Object>> roots = listOfMaps(seed.get("portalNavSections"));
        assertFalse(roots.isEmpty(), "portal menu seed must define root sections");

        Map<String, Object> screensRoot = roots
            .stream()
            .filter(node -> "sys.nav.portal.biScreens".equals(node.get("titleKey")))
            .findFirst()
            .orElse(null);
        assertNotNull(screensRoot, "数据大屏 must be a first-level root menu");
        assertEquals("bi/screens", screensRoot.get("path"));
        assertEquals("/bi/screens", screensRoot.get("externalLink"));

        Map<String, Object> biAppsRoot = roots
            .stream()
            .filter(node -> "sys.nav.portal.businessIntelligenceApps".equals(node.get("titleKey")))
            .findFirst()
            .orElse(null);
        assertNotNull(biAppsRoot, "商业智能应用 root menu must still exist");
        assertFalse(
            containsTitleKey(listOfMaps(biAppsRoot.get("children")), "sys.nav.portal.biScreens"),
            "商业智能应用 subtree must no longer own 数据大屏"
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
