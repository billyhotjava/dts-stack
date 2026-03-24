package com.yuzhi.dts.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.admin.IntegrationTest;
import com.yuzhi.dts.admin.domain.PortalMenu;
import com.yuzhi.dts.admin.domain.PortalMenuVisibility;
import com.yuzhi.dts.admin.repository.PortalMenuRepository;
import com.yuzhi.dts.admin.repository.PortalMenuVisibilityRepository;
import com.yuzhi.dts.admin.security.AuthoritiesConstants;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

@IntegrationTest
class PortalMenuServiceReplaceVisibilitiesIT {

    @Autowired
    private PortalMenuRepository portalMenuRepository;

    @Autowired
    private PortalMenuVisibilityRepository portalMenuVisibilityRepository;

    @Autowired
    private PortalMenuService portalMenuService;

    @MockBean
    private KeycloakAuthService keycloakAuthService;

    @BeforeEach
    void cleanUp() {
        portalMenuVisibilityRepository.deleteAllInBatch();
        portalMenuRepository.deleteAllInBatch();
    }

    @Test
    void findAllMenusOrderedShouldNotReapplyDefaultRoleBindingsAfterManualVisibilityChange() {
        portalMenuService.findAllMenusOrdered();

        PortalMenu menu = portalMenuRepository
            .findAll()
            .stream()
            .filter(item -> "visual-analytics/reports".equals(item.getPath()))
            .findFirst()
            .orElseThrow();

        portalMenuService.replaceVisibilities(menu, List.of(visibility(menu, AuthoritiesConstants.DEPT_LEADER)));

        assertThat(roleCodesOf(menu.getId())).containsExactly(AuthoritiesConstants.DEPT_LEADER);

        portalMenuService.findAllMenusOrdered();

        assertThat(roleCodesOf(menu.getId())).containsExactly(AuthoritiesConstants.DEPT_LEADER);
    }

    private PortalMenuVisibility visibility(PortalMenu menu, String roleCode) {
        PortalMenuVisibility visibility = new PortalMenuVisibility();
        visibility.setMenu(menu);
        visibility.setRoleCode(roleCode);
        visibility.setDataLevel("INTERNAL");
        return visibility;
    }

    private List<String> roleCodesOf(Long menuId) {
        return portalMenuVisibilityRepository
            .findByMenuId(menuId)
            .stream()
            .map(PortalMenuVisibility::getRoleCode)
            .sorted()
            .collect(Collectors.toList());
    }
}
