package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.directory.AdminDirectoryClient;
import com.yuzhi.dts.platform.service.directory.AdminDirectoryClient.OrgNode;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Directory APIs exposed to the platform frontend.
 * Provides organization tree endpoint used by dataset editor, IAM pages, etc.
 */
@RestController
@RequestMapping("/api/directory")
public class DirectoryResource {

    private final AdminDirectoryClient adminDirectoryClient;

    public DirectoryResource(AdminDirectoryClient adminDirectoryClient) {
        this.adminDirectoryClient = adminDirectoryClient;
    }

    @GetMapping("/orgs")
    public ApiResponse<List<OrgNode>> orgs() {
        List<OrgNode> tree = adminDirectoryClient.fetchOrgTree();
        return ApiResponses.ok(tree);
    }
}

