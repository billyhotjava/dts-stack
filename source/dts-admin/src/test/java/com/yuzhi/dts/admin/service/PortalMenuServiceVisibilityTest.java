package com.yuzhi.dts.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.domain.PortalMenu;
import com.yuzhi.dts.admin.domain.PortalMenuVisibility;
import com.yuzhi.dts.admin.repository.PortalMenuRepository;
import com.yuzhi.dts.admin.repository.PortalMenuVisibilityRepository;
import com.yuzhi.dts.admin.repository.SystemConfigRepository;
import com.yuzhi.dts.admin.security.AuthoritiesConstants;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class PortalMenuServiceVisibilityTest {

    @Mock
    private PortalMenuRepository menuRepository;

    @Mock
    private PortalMenuVisibilityRepository visibilityRepository;

    @Mock
    private SystemConfigRepository systemConfigRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private PortalMenuService portalMenuService;

    @BeforeEach
    void setUp() {
        portalMenuService = new PortalMenuService(
                menuRepository,
                visibilityRepository,
                systemConfigRepository,
                new ObjectMapper(),
                transactionManager);
    }

    @Test
    void findTreeForAudienceShouldNotBypassExplicitRoleBindingForTriadRoles() {
        PortalMenu analyticsMenu = menu("可视化分析", "/visualization");
        analyticsMenu.addVisibility(visibility(analyticsMenu, AuthoritiesConstants.DEPT_LEADER));
        analyticsMenu.addVisibility(visibility(analyticsMenu, AuthoritiesConstants.DEPT_DATA_OWNER));
        when(menuRepository.findByDeletedFalseAndParentIsNullOrderBySortOrderAscIdAsc()).thenReturn(List.of(analyticsMenu));

        List<PortalMenu> visibleMenus = portalMenuService.findTreeForAudience(
                Set.of(AuthoritiesConstants.AUTH_ADMIN),
                Set.of(),
                null);

        assertThat(visibleMenus).isEmpty();
    }

    @Test
    void findTreeForAudienceShouldNotBypassExplicitRoleBindingForOperatorAdmin() {
        PortalMenu analyticsMenu = menu("可视化分析", "/visualization");
        analyticsMenu.addVisibility(visibility(analyticsMenu, AuthoritiesConstants.DEPT_LEADER));
        analyticsMenu.addVisibility(visibility(analyticsMenu, AuthoritiesConstants.DEPT_DATA_OWNER));
        when(menuRepository.findByDeletedFalseAndParentIsNullOrderBySortOrderAscIdAsc()).thenReturn(List.of(analyticsMenu));

        List<PortalMenu> visibleMenus = portalMenuService.findTreeForAudience(
                Set.of(AuthoritiesConstants.OP_ADMIN),
                Set.of(),
                null);

        assertThat(visibleMenus).isEmpty();
    }

    @Test
    void findTreeForAudienceShouldKeepDefaultOperatorAdminAccessWhenNoVisibilityIsDefined() {
        PortalMenu analyticsMenu = menu("可视化分析", "/visualization");
        when(menuRepository.findByDeletedFalseAndParentIsNullOrderBySortOrderAscIdAsc()).thenReturn(List.of(analyticsMenu));

        List<PortalMenu> visibleMenus = portalMenuService.findTreeForAudience(
                Set.of(AuthoritiesConstants.OP_ADMIN),
                Set.of(),
                null);

        assertThat(visibleMenus).hasSize(1);
        assertThat(visibleMenus.get(0).getName()).isEqualTo("可视化分析");
    }

    private PortalMenu menu(String name, String path) {
        PortalMenu menu = new PortalMenu();
        menu.setId(100L);
        menu.setName(name);
        menu.setPath(path);
        menu.setSecurityLevel("GENERAL");
        menu.setDeleted(false);
        return menu;
    }

    private PortalMenuVisibility visibility(PortalMenu menu, String roleCode) {
        PortalMenuVisibility visibility = new PortalMenuVisibility();
        visibility.setMenu(menu);
        visibility.setRoleCode(roleCode);
        visibility.setDataLevel("INTERNAL");
        return visibility;
    }
}
