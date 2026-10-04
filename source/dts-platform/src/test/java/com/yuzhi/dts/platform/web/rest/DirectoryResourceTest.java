package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway;
import java.util.List;
import org.junit.jupiter.api.Test;

class DirectoryResourceTest {

    @Test
    void orgsShouldReturnPlatformApiResponseWrappedAroundGatewayData() {
        AdminDirectoryGateway gateway = mock(AdminDirectoryGateway.class);
        when(gateway.fetchOrgTree()).thenReturn(List.of(node(1L, "研究所", "1000")));

        DirectoryResource resource = new DirectoryResource(gateway);

        ApiResponse<List<AdminDirectoryGateway.OrgNode>> response = resource.orgs();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getName()).isEqualTo("研究所");
    }

    @Test
    void usersAndRolesShouldDelegateToGateway() {
        AdminDirectoryGateway gateway = mock(AdminDirectoryGateway.class);
        when(gateway.searchUsers("alice"))
            .thenReturn(List.of(new AdminDirectoryGateway.UserSummary("u1", "alice", "Alice", "1001", null)));
        when(gateway.listRoles())
            .thenReturn(List.of(new AdminDirectoryGateway.RoleSummary("r1", "ROLE_DATA_ADMIN", "管理员", null, List.of(), "platform")));

        DirectoryResource resource = new DirectoryResource(gateway);

        ApiResponse<List<AdminDirectoryGateway.UserSummary>> userResponse = resource.users("alice");
        ApiResponse<List<AdminDirectoryGateway.RoleSummary>> roleResponse = resource.roles();

        assertThat(userResponse.getData()).containsExactly(new AdminDirectoryGateway.UserSummary("u1", "alice", "Alice", "1001", null));
        assertThat(roleResponse.getData())
            .containsExactly(new AdminDirectoryGateway.RoleSummary("r1", "ROLE_DATA_ADMIN", "管理员", null, List.of(), "platform"));
    }

    private AdminDirectoryGateway.OrgNode node(Long id, String name, String deptCode) {
        AdminDirectoryGateway.OrgNode node = new AdminDirectoryGateway.OrgNode();
        node.setId(id);
        node.setName(name);
        node.setDeptCode(deptCode);
        node.setIsRoot(true);
        return node;
    }
}
