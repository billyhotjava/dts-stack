package com.yuzhi.dts.admin.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.admin.domain.PortalMenu;
import com.yuzhi.dts.admin.repository.AdminDatasetRepository;
import com.yuzhi.dts.admin.repository.OrganizationRepository;
import com.yuzhi.dts.admin.repository.PortalMenuRepository;
import com.yuzhi.dts.admin.repository.SystemConfigRepository;
import com.yuzhi.dts.admin.service.OrganizationService;
import com.yuzhi.dts.admin.service.PortalMenuService;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

class DevDataSeederTest {

    @Test
    void seedMenusClearsSeedRoleBindingsWhenMenusAlreadyExist() throws Exception {
        PortalMenuRepository menuRepository = mock(PortalMenuRepository.class);
        PortalMenuService portalMenuService = mock(PortalMenuService.class);
        PortalMenu existingRoot = new PortalMenu();
        existingRoot.setId(1L);
        when(menuRepository.findByDeletedFalseAndParentIsNullOrderBySortOrderAscIdAsc()).thenReturn(List.of(existingRoot));
        DevDataSeeder seeder = new DevDataSeeder(
            mock(OrganizationService.class),
            mock(OrganizationRepository.class),
            mock(AdminDatasetRepository.class),
            menuRepository,
            portalMenuService,
            mock(SystemConfigRepository.class),
            mock(Environment.class)
        );

        Method seedMenus = DevDataSeeder.class.getDeclaredMethod("seedMenus");
        seedMenus.setAccessible(true);
        seedMenus.invoke(seeder);

        verify(portalMenuService).clearSeedMenuRoleBindings();
    }
}
