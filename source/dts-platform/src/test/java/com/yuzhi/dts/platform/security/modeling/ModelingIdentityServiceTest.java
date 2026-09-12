package com.yuzhi.dts.platform.security.modeling;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway.ModelingUser;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ModelingIdentityServiceTest {
    private final AdminDirectoryGateway directory = mock(AdminDirectoryGateway.class);
    private final ModelingIdentityService identities = new ModelingIdentityService(directory);
    private ModelingUser user(String role, boolean enabled) { return new ModelingUser("stable-id", "renamed-login", "张三", "dept-a", "部门甲", List.of(role), enabled, "GENERAL"); }
    @Test void legacySessionMustReloginWithoutUsernameFallback() {
        assertThatThrownBy(() -> identities.resolve(null)).isInstanceOfSatisfying(ModelingIdentityException.class, ex -> assertThat(ex.status()).isEqualTo(401));
        verifyNoInteractions(directory);
    }
    @Test void usesStableIdOnlyInsideModelingAndRestoresLegacyIdentityEvenOnFailure() {
        var previous = new TestingAuthenticationToken("legacy-username", "unused", "ROLE_EMPLOYEE");
        SecurityContextHolder.getContext().setAuthentication(previous);
        when(directory.currentModelingUser("stable-id")).thenReturn(user(AuthoritiesConstants.DEPT_DATA_OWNER,true));
        assertThatThrownBy(() -> identities.asCurrentUser("stable-id", () -> {
            assertThat(SecurityUtils.getCurrentUserId()).contains("stable-id");
            assertThat(ModelingIdentity.current().username()).isEqualTo("renamed-login");
            throw new IllegalStateException("downstream failed");
        })).hasMessage("downstream failed");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(previous);
        assertThat(ModelingIdentity.optional()).isEmpty(); SecurityContextHolder.clearContext();
    }
    @Test void revokedRoleDisabledUserAndDirectoryFailureNeverReuseAllowedIdentity() {
        when(directory.currentModelingUser("stable-id")).thenReturn(user(AuthoritiesConstants.DEPT_DATA_OWNER,true), user(AuthoritiesConstants.EMPLOYEE,true), user(AuthoritiesConstants.DEPT_DATA_OWNER,false)).thenThrow(new IllegalStateException("directory down"));
        assertThat(identities.asCurrentUser("stable-id", () -> true)).isTrue();
        assertStatus(403); assertStatus(401); assertStatus(503);
    }
    @Test void exactDepartmentComparisonNeverMatchesSuffixOrParent() {
        identities.withIdentity(user(AuthoritiesConstants.DEPT_DATA_OWNER,true), () -> {
            assertThat(ModelingIdentity.department("dept-a")).isTrue();
            assertThat(ModelingIdentity.department("root:dept-a")).isFalse();
            assertThat(ModelingIdentity.department("dept-a-child")).isFalse(); return null;
        });
    }
    private void assertStatus(int status) { assertThatThrownBy(() -> identities.resolve("stable-id")).isInstanceOfSatisfying(ModelingIdentityException.class, ex -> assertThat(ex.status()).isEqualTo(status)); }
}
