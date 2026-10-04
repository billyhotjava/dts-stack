package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway.OrgNode;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway.UserSummary;
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

    private final AdminDirectoryGateway adminDirectoryGateway;

    public DirectoryResource(AdminDirectoryGateway adminDirectoryGateway) {
        this.adminDirectoryGateway = adminDirectoryGateway;
    }

    @GetMapping("/orgs")
    public ApiResponse<List<OrgNode>> orgs() {
        List<OrgNode> tree = adminDirectoryGateway.fetchOrgTree();
        return ApiResponses.ok(tree);
    }

    @GetMapping("/users")
    public ApiResponse<List<UserSummary>> users(@RequestParam(name = "keyword", required = false) String keyword) {
        List<UserSummary> list = adminDirectoryGateway.searchUsers(keyword);
        return ApiResponses.ok(list);
    }

    @GetMapping("/roles")
    public ApiResponse<List<AdminDirectoryGateway.RoleSummary>> roles() {
        List<AdminDirectoryGateway.RoleSummary> roles = adminDirectoryGateway.listRoles();
        return ApiResponses.ok(roles);
    }
}
