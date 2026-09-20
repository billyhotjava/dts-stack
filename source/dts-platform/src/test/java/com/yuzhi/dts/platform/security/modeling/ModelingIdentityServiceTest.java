package com.yuzhi.dts.platform.security.modeling;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway.ModelingUser;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ModelingIdentityServiceTest {
    private final AdminDirectoryGateway directory = mock(AdminDirectoryGateway.class);
    private final ModelingIdentityService identities = new ModelingIdentityService(directory);
    private ModelingUser user(String role, boolean enabled) { return new ModelingUser("stable-id", "renamed-login", "张三", "dept-a", "部门甲", List.of(role), enabled, "GENERAL"); }

    @AfterEach
    void clearIdentity() {
        ModelingIdentity.set(null);
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "\t" })
    @DisplayName("F11-UT-010：缺少稳定主体时不查询用户名或执行业务")
    void missingStableSubjectDoesNotCallDirectory(String subjectId) {
        Runnable business = mock(Runnable.class);

        assertThatThrownBy(() -> identities.asCurrentUser(subjectId, () -> {
            business.run();
            return null;
        })).isInstanceOfSatisfying(ModelingIdentityException.class, ex -> assertThat(ex.status()).isEqualTo(401));

        verifyNoInteractions(directory, business);
        assertThat(ModelingIdentity.optional()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidDirectoryResponses")
    @DisplayName("F11-UT-010/016：错误主体或缺必需字段不能产生允许上下文")
    void rejectsMalformedOrDifferentSubjects(ModelingUser response) {
        when(directory.currentModelingUser("stable-id")).thenReturn(response);
        Runnable business = mock(Runnable.class);

        assertThatThrownBy(() -> identities.asCurrentUser("stable-id", () -> {
            business.run();
            return null;
        })).isInstanceOfSatisfying(ModelingIdentityException.class, ex -> assertThat(ex.status()).isEqualTo(503));

        verify(directory).currentModelingUser("stable-id");
        verifyNoMoreInteractions(directory);
        verifyNoInteractions(business);
        assertThat(ModelingIdentity.optional()).isEmpty();
    }

    static Stream<Arguments> invalidDirectoryResponses() {
        List<String> roles = List.of(AuthoritiesConstants.DEPT_DATA_OWNER);
        return Stream.of(
            Arguments.of((Object) null),
            Arguments.of(new ModelingUser("replacement-id", "same-login", "新账号", "dept-a", "部门甲", roles, true, "GENERAL")),
            Arguments.of(new ModelingUser("stable-id", "same-login", "原账号", "dept-a", "部门甲", null, true, "GENERAL")),
            Arguments.of(new ModelingUser("stable-id", "same-login", "原账号", "dept-a", "部门甲", roles, null, "GENERAL")),
            Arguments.of(new ModelingUser("stable-id", null, "原账号", "dept-a", "部门甲", roles, true, "GENERAL"))
        );
    }

    @ParameterizedTest
    @MethodSource("directoryFailures")
    @DisplayName("F11-UT-013：目录不存在和目录故障具有不同错误语义且不暴露上游内容")
    void mapsDirectoryFailuresWithoutLeakingUpstreamDetails(Integer upstreamStatus, int expectedStatus) {
        when(directory.currentModelingUser("stable-id")).thenThrow(
            new AdminGatewayException("internal-sensitive-detail", upstreamStatus, "/internal/test-only")
        );

        assertThatThrownBy(() -> identities.resolve("stable-id"))
            .isInstanceOfSatisfying(ModelingIdentityException.class, ex -> assertThat(ex.status()).isEqualTo(expectedStatus))
            .hasMessageNotContaining("internal-sensitive-detail")
            .hasMessageNotContaining("/internal/test-only");
        verify(directory).currentModelingUser("stable-id");
        verifyNoMoreInteractions(directory);
    }

    static Stream<Arguments> directoryFailures() {
        return Stream.of(Arguments.of(404, 401), Arguments.of(500, 503), Arguments.of(403, 503), Arguments.of(null, 503));
    }

    @Test
    @DisplayName("F11-UT-024/036：边界内复用，下一边界读取当前部门而不沿用会话快照")
    void refreshesDepartmentAtTheNextBoundary() {
        AtomicReference<ModelingUser> current = new AtomicReference<>(
            new ModelingUser("stable-id", "login", "测试用户", "1153", "部门甲", List.of(AuthoritiesConstants.DEPT_DATA_OWNER), true, "GENERAL")
        );
        when(directory.currentModelingUser("stable-id")).thenAnswer(invocation -> current.get());

        identities.asCurrentUser("stable-id", () -> {
            assertThat(ModelingIdentity.department("1153")).isTrue();
            assertThat(ModelingIdentity.department("153")).isFalse();
            assertThat(ModelingIdentity.department("1153-child")).isFalse();
            current.set(new ModelingUser("stable-id", "renamed-login", "测试用户", "153", "部门乙", List.of(AuthoritiesConstants.DEPT_DATA_OWNER), true, "GENERAL"));
            assertThat(ModelingIdentity.department("1153")).as("同一授权边界的已取得上下文").isTrue();
            return null;
        });
        verify(directory, times(1)).currentModelingUser("stable-id");
        assertThat(ModelingIdentity.optional()).isEmpty();

        identities.asCurrentUser("stable-id", () -> {
            assertThat(ModelingIdentity.department("1153")).isFalse();
            assertThat(ModelingIdentity.department("153")).isTrue();
            assertThat(ModelingIdentity.current().username()).isEqualTo("renamed-login");
            return null;
        });
        verify(directory, times(2)).currentModelingUser("stable-id");
        verifyNoMoreInteractions(directory);
    }

    @Test
    @DisplayName("F11-UT-010/029：嵌套授权失败后恢复外层身份和原认证上下文")
    void restoresEachContextWhenNestedExecutionFails() {
        var original = new TestingAuthenticationToken("original-login", "unused", "ROLE_EMPLOYEE");
        SecurityContextHolder.getContext().setAuthentication(original);
        when(directory.currentModelingUser("stable-id")).thenReturn(user(AuthoritiesConstants.DEPT_DATA_OWNER, true));
        when(directory.currentModelingUser("second-id")).thenReturn(
            new ModelingUser("second-id", "second-login", "第二用户", "153", "部门乙", List.of(AuthoritiesConstants.DEPT_DATA_OWNER), true, "GENERAL")
        );

        identities.asCurrentUser("stable-id", () -> {
            var outerAuthentication = SecurityContextHolder.getContext().getAuthentication();
            assertThatThrownBy(() -> identities.asCurrentUser("second-id", () -> {
                assertThat(ModelingIdentity.current().id()).isEqualTo("second-id");
                assertThat(SecurityUtils.getCurrentUserId()).contains("second-id");
                throw new IllegalStateException("business failed");
            })).hasMessage("business failed");
            assertThat(ModelingIdentity.current().id()).isEqualTo("stable-id");
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(outerAuthentication);
            return null;
        });

        assertThat(ModelingIdentity.optional()).isEmpty();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(original);
    }

    @Test
    @DisplayName("F11-UT-026：人员密级缺失不从角色或部门推导高等级")
    void doesNotInventPersonnelLevelDuringIdentityResolution() {
        when(directory.currentModelingUser("stable-id")).thenReturn(
            new ModelingUser("stable-id", "login", "测试用户", "1153", "部门甲", List.of(AuthoritiesConstants.DEPT_DATA_OWNER), true, null)
        );

        identities.asCurrentUser("stable-id", () -> {
            assertThat(ModelingIdentity.current().personnelLevel()).isNull();
            var principal = (org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
            assertThat(principal.getAttributes()).doesNotContainKey("personnel_level");
            return null;
        });
    }
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
