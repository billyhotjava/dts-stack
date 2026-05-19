package com.yuzhi.dts.admin.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
