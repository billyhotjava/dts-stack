package com.yuzhi.dts.platform.service.directory;

import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AdminUserDirectoryClient {

    private final AdminDirectoryGateway gateway;

    public AdminUserDirectoryClient(AdminDirectoryGateway gateway) {
        this.gateway = gateway;
    }

    public List<UserSummary> searchUsers(String keyword) {
        return gateway.searchUsers(keyword)
            .stream()
            .map(item -> new UserSummary(item.id(), item.username(), item.displayName(), item.deptCode()))
            .toList();
    }

    public List<RoleSummary> listRoles() {
        return gateway.listRoles()
            .stream()
            .map(item -> new RoleSummary(item.id(), item.name(), item.description(), item.scope(), item.operations(), item.source()))
            .toList();
    }

    public record UserSummary(String id, String username, String displayName, String deptCode) {}

    public record RoleSummary(String id, String name, String description, String scope, List<String> operations, String source) {}
}
