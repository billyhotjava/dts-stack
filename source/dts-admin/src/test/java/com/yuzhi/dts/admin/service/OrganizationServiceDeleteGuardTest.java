package com.yuzhi.dts.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.admin.config.MdmGatewayProperties;
import com.yuzhi.dts.admin.domain.OrganizationNode;
import com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository;
import com.yuzhi.dts.admin.repository.OrganizationRepository;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAdminClient;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 部门删除保护：部门下有人一律不能删。
 *
 * <p>此前这个判断读的是 person_profile，而该表自 4d53821ba 起再无写入方，
 * 冻结数据既会误拦空部门，也会漏放调岗后才进来的人。现在改读 Keycloak 快照，
 * 并遵循「查不准就不删」。
 */
class OrganizationServiceDeleteGuardTest {

    private static final String DEPT_CODE = "D001";

    @Test
    @DisplayName("快照中该部门仍有人时拒绝删除")
    void deleteRejectedWhenSnapshotStillHasMembers() {
        OrganizationRepository repository = mock(OrganizationRepository.class);
        AdminKeycloakUserRepository users = mock(AdminKeycloakUserRepository.class);
        OrganizationNode node = node();
        when(repository.findById(1L)).thenReturn(Optional.of(node));
        when(repository.existsByParent_Id(1L)).thenReturn(false);
        when(users.existsByDeptCodeIgnoreCase(DEPT_CODE)).thenReturn(true);

        OrganizationService service = buildService(repository, users, mock(KeycloakAdminClient.class));

        assertThatThrownBy(() -> service.delete(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("仍有关联用户");
        verify(repository, never()).delete(node);
    }

    @Test
    @DisplayName("快照已同步且该部门无人时允许删除")
    void deleteAllowedWhenSnapshotSyncedAndDepartmentEmpty() {
        OrganizationRepository repository = mock(OrganizationRepository.class);
        AdminKeycloakUserRepository users = mock(AdminKeycloakUserRepository.class);
        OrganizationNode node = node();
        when(repository.findById(1L)).thenReturn(Optional.of(node));
        when(repository.existsByParent_Id(1L)).thenReturn(false);
        when(users.existsByDeptCodeIgnoreCase(DEPT_CODE)).thenReturn(false);
        when(users.existsByDeptCodeIsNotNull()).thenReturn(true);

        OrganizationService service = buildService(repository, users, mock(KeycloakAdminClient.class));

        assertThatCode(() -> service.delete(1L)).doesNotThrowAnyException();
        verify(repository).delete(node);
    }

    @Test
    @DisplayName("快照尚未回填部门信息时拒绝删除，而不是当作空部门放行")
    void deleteRejectedWhenSnapshotDeptNotBackfilled() {
        OrganizationRepository repository = mock(OrganizationRepository.class);
        AdminKeycloakUserRepository users = mock(AdminKeycloakUserRepository.class);
        OrganizationNode node = node();
        when(repository.findById(1L)).thenReturn(Optional.of(node));
        when(repository.existsByParent_Id(1L)).thenReturn(false);
        when(users.existsByDeptCodeIgnoreCase(DEPT_CODE)).thenReturn(false);
        when(users.existsByDeptCodeIsNotNull()).thenReturn(false);

        OrganizationService service = buildService(repository, users, mock(KeycloakAdminClient.class));

        assertThatThrownBy(() -> service.delete(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("无法确认");
        verify(repository, never()).delete(node);
    }

    @Test
    @DisplayName("Keycloak 组里还有成员时拒绝删除，即使本地快照说该部门为空")
    void deleteRejectedWhenKeycloakGroupStillHasMembers() {
        OrganizationRepository repository = mock(OrganizationRepository.class);
        AdminKeycloakUserRepository users = mock(AdminKeycloakUserRepository.class);
        KeycloakAdminClient keycloak = mock(KeycloakAdminClient.class);
        OrganizationNode node = node();
        node.setKeycloakGroupId("grp-1");
        when(repository.findById(1L)).thenReturn(Optional.of(node));
        when(repository.existsByParent_Id(1L)).thenReturn(false);
        when(users.existsByDeptCodeIgnoreCase(DEPT_CODE)).thenReturn(false);
        when(users.existsByDeptCodeIsNotNull()).thenReturn(true);
        when(keycloak.listGroupMembers(anyString(), anyInt(), anyInt(), anyString())).thenReturn(List.of(new KeycloakUserDTO()));

        OrganizationService service = buildServiceWithKeycloakSync(repository, users, keycloak);

        assertThatThrownBy(() -> service.delete(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("仍有关联用户");
        verify(repository, never()).delete(node);
    }

    @Test
    @DisplayName("Keycloak 查询失败时拒绝删除，不按无人处理")
    void deleteRejectedWhenKeycloakLookupFails() {
        OrganizationRepository repository = mock(OrganizationRepository.class);
        AdminKeycloakUserRepository users = mock(AdminKeycloakUserRepository.class);
        KeycloakAdminClient keycloak = mock(KeycloakAdminClient.class);
        OrganizationNode node = node();
        node.setKeycloakGroupId("grp-1");
        when(repository.findById(1L)).thenReturn(Optional.of(node));
        when(repository.existsByParent_Id(1L)).thenReturn(false);
        when(users.existsByDeptCodeIgnoreCase(DEPT_CODE)).thenReturn(false);
        when(users.existsByDeptCodeIsNotNull()).thenReturn(true);
        when(keycloak.listGroupMembers(anyString(), anyInt(), anyInt(), anyString()))
            .thenThrow(new IllegalStateException("查询 Keycloak 组成员失败"));

        OrganizationService service = buildServiceWithKeycloakSync(repository, users, keycloak);

        assertThatThrownBy(() -> service.delete(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("无法确认");
        verify(repository, never()).delete(node);
    }

    @Test
    @DisplayName("组织没有编码时，按节点 ID 识别快照里的成员")
    void deleteRejectedWhenSnapshotReferencesNodeId() {
        OrganizationRepository repository = mock(OrganizationRepository.class);
        AdminKeycloakUserRepository users = mock(AdminKeycloakUserRepository.class);
        OrganizationNode node = node();
        node.setDeptCode(null);
        when(repository.findById(1L)).thenReturn(Optional.of(node));
        when(repository.existsByParent_Id(1L)).thenReturn(false);
        when(users.existsByDeptCodeIgnoreCase("1")).thenReturn(true);

        OrganizationService service = buildService(repository, users, mock(KeycloakAdminClient.class));

        assertThatThrownBy(() -> service.delete(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("仍有关联用户");
        verify(repository, never()).delete(node);
    }

    @Test
    @DisplayName("组织没有编码、快照已同步且无人时允许删除")
    void deleteAllowedForNodeWithoutDeptCodeWhenEmpty() {
        OrganizationRepository repository = mock(OrganizationRepository.class);
        AdminKeycloakUserRepository users = mock(AdminKeycloakUserRepository.class);
        OrganizationNode node = node();
        node.setDeptCode(null);
        when(repository.findById(1L)).thenReturn(Optional.of(node));
        when(repository.existsByParent_Id(1L)).thenReturn(false);
        when(users.existsByDeptCodeIsNotNull()).thenReturn(true);

        OrganizationService service = buildService(repository, users, mock(KeycloakAdminClient.class));

        assertThatCode(() -> service.delete(1L)).doesNotThrowAnyException();
        verify(repository).delete(node);
    }

    @Test
    @DisplayName("子部门检查仍然先于人员检查")
    void deleteRejectedWhenChildDepartmentExists() {
        OrganizationRepository repository = mock(OrganizationRepository.class);
        AdminKeycloakUserRepository users = mock(AdminKeycloakUserRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.of(node()));
        when(repository.existsByParent_Id(1L)).thenReturn(true);

        OrganizationService service = buildService(repository, users, mock(KeycloakAdminClient.class));

        assertThatThrownBy(() -> service.delete(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("子部门");
        verify(users, never()).existsByDeptCodeIgnoreCase(anyString());
    }

    private OrganizationNode node() {
        OrganizationNode node = new OrganizationNode();
        node.setId(1L);
        node.setName("测试部门");
        node.setDeptCode(DEPT_CODE);
        return node;
    }

    private OrganizationService buildService(
        OrganizationRepository repository,
        AdminKeycloakUserRepository users,
        KeycloakAdminClient keycloak
    ) {
        // 未配置管理客户端 => Keycloak 同步关闭，只走本地快照判断
        return new OrganizationService(
            repository,
            users,
            keycloak,
            mock(KeycloakAuthService.class),
            "",
            "",
            false,
            "",
            "",
            "",
            "",
            new MdmGatewayProperties()
        );
    }

    private OrganizationService buildServiceWithKeycloakSync(
        OrganizationRepository repository,
        AdminKeycloakUserRepository users,
        KeycloakAdminClient keycloak
    ) {
        KeycloakAuthService auth = mock(KeycloakAuthService.class);
        when(auth.obtainClientCredentialsToken(anyString(), anyString()))
            .thenReturn(new KeycloakAuthService.TokenResponse("token", null, null, null, null, null, null, null));
        OrganizationService service = new OrganizationService(
            repository,
            users,
            keycloak,
            auth,
            "mgmt-client",
            "mgmt-secret",
            true,
            "",
            "",
            "",
            "",
            new MdmGatewayProperties()
        );
        assertThat(service).isNotNull();
        return service;
    }
}
