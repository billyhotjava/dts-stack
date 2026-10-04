package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.governance.GovDimensionDictionary;
import com.yuzhi.dts.platform.repository.governance.GovDimensionDictionaryRepository;
import com.yuzhi.dts.platform.repository.governance.GovDimensionItemRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class DimensionDepartmentScopeTest {

    private final GovDimensionDictionaryRepository dimensions = mock(GovDimensionDictionaryRepository.class);
    private final GovDimensionItemRepository items = mock(GovDimensionItemRepository.class);
    private final AccessChecker accessChecker = mock(AccessChecker.class);
    private final OrganizationVisibilityService organizations = mock(OrganizationVisibilityService.class);
    private final DimensionService dimensionService = new DimensionService(dimensions, accessChecker, organizations);
    private final DimensionItemService itemService = new DimensionItemService(dimensions, items, accessChecker, organizations);

    @BeforeEach
    void setUp() {
        when(accessChecker.resolveHighestDataLevel()).thenReturn(DataLevel.DATA_CONFIDENTIAL);
        when(dimensions.save(any(GovDimensionDictionary.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void departmentDataOwnerCannotMutateGlobalDimensionWhileInstituteDataOwnerCan() {
        UUID id = UUID.randomUUID();
        GovDimensionDictionary global = dimension(id, "ROOT");
        when(dimensions.findById(id)).thenReturn(Optional.of(global));
        when(organizations.isRoot("ROOT")).thenReturn(true);

        authenticate(AuthoritiesConstants.DEPT_DATA_OWNER, "DEPT_A");
        assertThatThrownBy(() -> dimensionService.publish(id, "DEPT_A"))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("department");

        SecurityContextHolder.clearContext();
        authenticate(AuthoritiesConstants.INST_DATA_OWNER, "ROOT");
        assertThat(dimensionService.publish(id, null).getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void departmentHeaderCannotBeForgedForDimensionOrDictionaryItemWrites() {
        authenticate(AuthoritiesConstants.DEPT_DATA_OWNER, "DEPT_A");

        assertThatThrownBy(() -> dimensionService.list(null, null, null, "DEPT_B"))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("department");
        assertThatThrownBy(() -> itemService.create(UUID.randomUUID(), "DEPT_B", null))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("department");

        verify(dimensions, never()).findAll();
        verify(dimensions, never()).findById(any(UUID.class));
    }

    private static GovDimensionDictionary dimension(UUID id, String ownerDepartment) {
        GovDimensionDictionary value = new GovDimensionDictionary();
        value.setId(id);
        value.setCode("TEST_DIM");
        value.setName("测试维度");
        value.setOwnerDept(ownerDepartment);
        value.setStatus("DRAFT");
        value.setDataLevel("DATA_INTERNAL");
        return value;
    }

    private static void authenticate(String role, String department) {
        Jwt jwt = Jwt
            .withTokenValue("test-token")
            .header("alg", "none")
            .subject("actor")
            .claim("roles", List.of(role))
            .claim("dept_code", department)
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
