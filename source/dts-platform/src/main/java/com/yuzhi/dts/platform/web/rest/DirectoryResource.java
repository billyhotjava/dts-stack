package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.directory.AdminDirectoryClient;
import com.yuzhi.dts.platform.service.directory.AdminDirectoryClient.OrgNode;
import com.yuzhi.dts.platform.service.directory.AdminUserDirectoryClient;
import com.yuzhi.dts.platform.service.directory.AdminUserDirectoryClient.UserSummary;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Directory APIs exposed to the platform frontend.
 * Provides organization tree endpoint used by dataset editor, IAM pages, etc.
 */
@RestController
@RequestMapping("/api/directory")
public class DirectoryResource {

    private final AdminDirectoryClient adminDirectoryClient;
    private final AdminUserDirectoryClient adminUserDirectoryClient;

    public DirectoryResource(AdminDirectoryClient adminDirectoryClient, AdminUserDirectoryClient adminUserDirectoryClient) {
        this.adminDirectoryClient = adminDirectoryClient;
        this.adminUserDirectoryClient = adminUserDirectoryClient;
    }

    @GetMapping("/orgs")
    public ApiResponse<List<OrgNode>> orgs() {
        List<OrgNode> tree = adminDirectoryClient.fetchOrgTree();
        return ApiResponses.ok(tree);
    }

    @GetMapping("/users")
    public ApiResponse<List<UserSummary>> users(@RequestParam(name = "keyword", required = false) String keyword) {
        List<UserSummary> list = adminUserDirectoryClient.searchUsers(keyword);
        return ApiResponses.ok(list);
    }

    @GetMapping("/roles")
    public ApiResponse<List<AdminUserDirectoryClient.RoleSummary>> roles() {
        List<AdminUserDirectoryClient.RoleSummary> roles = adminUserDirectoryClient.listRoles();
        return ApiResponses.ok(roles);
    }
}
