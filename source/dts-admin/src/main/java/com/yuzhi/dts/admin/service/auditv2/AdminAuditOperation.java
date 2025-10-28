package com.yuzhi.dts.admin.service.auditv2;

import java.util.Locale;
import java.util.Optional;

/**
 * 管理端内置操作元数据，供审批审计等场景引用。
 */
public enum AdminAuditOperation {
    PORTAL_MENU_FETCH("PORTAL_MENU_FETCH", "查询门户菜单", AuditOperationType.READ, "admin.portal-menus", "门户菜单", "portal_menu"),
    ADMIN_MENU_VIEW_TREE("ADMIN_MENU_VIEW_TREE", "查看门户菜单树", AuditOperationType.READ, "admin.portal-menus", "门户菜单", "portal_menu"),
    ADMIN_USER_VIEW("ADMIN_USER_VIEW", "查看用户列表", AuditOperationType.READ, "admin.users", "用户管理", "admin_keycloak_user"),
    ADMIN_USER_CREATE("ADMIN_USER_CREATE", "新增用户", AuditOperationType.CREATE, "admin.users", "用户管理", "admin_keycloak_user"),
    ADMIN_USER_UPDATE("ADMIN_USER_UPDATE", "修改用户", AuditOperationType.UPDATE, "admin.users", "用户管理", "admin_keycloak_user"),
    ADMIN_USER_DELETE("ADMIN_USER_DELETE", "删除用户", AuditOperationType.DELETE, "admin.users", "用户管理", "admin_keycloak_user"),
    ADMIN_USER_ENABLE("ADMIN_USER_ENABLE", "启用用户", AuditOperationType.ENABLE, "admin.users", "用户管理", "admin_keycloak_user"),
    ADMIN_USER_DISABLE("ADMIN_USER_DISABLE", "禁用用户", AuditOperationType.DISABLE, "admin.users", "用户管理", "admin_keycloak_user"),
    ADMIN_USER_RESET_PASSWORD("ADMIN_USER_RESET_PASSWORD", "重置用户密码", AuditOperationType.UPDATE, "admin.users", "用户管理", "admin_keycloak_user"),
    ADMIN_USER_ASSIGN_ROLE("ADMIN_USER_ASSIGN_ROLE", "调整用户角色", AuditOperationType.UPDATE, "admin.users", "用户管理", "admin_role_assignment"),
    ADMIN_GROUP_CREATE("ADMIN_GROUP_CREATE", "新增用户组", AuditOperationType.CREATE, "admin.groups", "用户组", "admin_group"),
    ADMIN_GROUP_DELETE("ADMIN_GROUP_DELETE", "删除用户组", AuditOperationType.DELETE, "admin.groups", "用户组", "admin_group"),
    ADMIN_ROLE_ASSIGNMENT_CREATE("ADMIN_ROLE_ASSIGNMENT_CREATE", "授予角色", AuditOperationType.GRANT, "admin.roles", "角色管理", "admin_role_assignment"),
    ADMIN_ROLE_CREATE("ADMIN_ROLE_CREATE", "新增角色", AuditOperationType.CREATE, "admin.roles", "角色管理", "admin_custom_role"),
    ADMIN_ROLE_VIEW("ADMIN_ROLE_VIEW", "查看角色", AuditOperationType.READ, "admin.roles", "角色管理", "admin_custom_role"),
    ADMIN_ROLE_UPDATE("ADMIN_ROLE_UPDATE", "修改角色", AuditOperationType.UPDATE, "admin.roles", "角色管理", "admin_custom_role"),
    ADMIN_ROLE_DELETE("ADMIN_ROLE_DELETE", "删除角色", AuditOperationType.DELETE, "admin.roles", "角色管理", "admin_custom_role"),
    ADMIN_MENU_CREATE("ADMIN_MENU_CREATE", "新增门户菜单", AuditOperationType.CREATE, "admin.portal-menus", "门户菜单", "portal_menu"),
    ADMIN_MENU_UPDATE("ADMIN_MENU_UPDATE", "修改门户菜单", AuditOperationType.UPDATE, "admin.portal-menus", "门户菜单", "portal_menu"),
    ADMIN_MENU_DISABLE("ADMIN_MENU_DISABLE", "禁用门户菜单", AuditOperationType.DISABLE, "admin.portal-menus", "门户菜单", "portal_menu"),
    ADMIN_DATA_SOURCE_CREATE("ADMIN_DATA_SOURCE_CREATE", "新增数据源", AuditOperationType.CREATE, "admin.infra", "基础设施", "infra_data_source"),
    ADMIN_DATA_SOURCE_REFRESH("ADMIN_DATA_SOURCE_REFRESH", "刷新数据源注册信息", AuditOperationType.REFRESH, "admin.infra", "基础设施", "infra_data_source"),
    ADMIN_DATA_SOURCE_DELETE("ADMIN_DATA_SOURCE_DELETE", "删除数据源", AuditOperationType.DELETE, "admin.infra", "基础设施", "infra_data_source"),
    ADMIN_ORG_CREATE("ADMIN_ORG_CREATE", "新增组织节点", AuditOperationType.CREATE, "admin.orgs", "组织机构", "organization_node"),
    ADMIN_ORG_UPDATE("ADMIN_ORG_UPDATE", "修改组织节点", AuditOperationType.UPDATE, "admin.orgs", "组织机构", "organization_node"),
    ADMIN_ORG_DELETE("ADMIN_ORG_DELETE", "删除组织节点", AuditOperationType.DELETE, "admin.orgs", "组织机构", "organization_node"),
    ADMIN_ORG_VIEW_TREE("ADMIN_ORG_VIEW_TREE", "查看组织树", AuditOperationType.READ, "admin.orgs", "组织机构", "organization_node"),
    ADMIN_CHANGE_REQUEST_MANAGE("ADMIN_CHANGE_REQUEST_MANAGE", "提交或处理变更单", AuditOperationType.CREATE, "admin.change-requests", "变更请求", "change_request"),
    ADMIN_CHANGE_REQUEST_VIEW("ADMIN_CHANGE_REQUEST_VIEW", "查看变更请求", AuditOperationType.READ, "admin.change-requests", "变更请求", "change_request"),
    ADMIN_APPROVAL_APPLY("ADMIN_APPROVAL_APPLY", "执行审批项", AuditOperationType.EXECUTE, "admin.approvals", "审批管理", "admin_approval_item"),
    ADMIN_APPROVAL_DECIDE("ADMIN_APPROVAL_DECIDE", "审批变更单", AuditOperationType.APPROVE, "admin.approvals", "审批管理", "change_request"),
    ADMIN_APPROVAL_VIEW("ADMIN_APPROVAL_VIEW", "查看审批请求", AuditOperationType.READ, "admin.approvals", "审批管理", "change_request"),
    ADMIN_CUSTOM_ROLE_CREATE("ADMIN_CUSTOM_ROLE_CREATE", "新增自定义角色", AuditOperationType.CREATE, "admin.roles", "角色管理", "admin_custom_role"),
    ADMIN_CUSTOM_ROLE_UPDATE("ADMIN_CUSTOM_ROLE_UPDATE", "修改自定义角色", AuditOperationType.UPDATE, "admin.roles", "角色管理", "admin_custom_role"),
    ADMIN_CUSTOM_ROLE_DELETE("ADMIN_CUSTOM_ROLE_DELETE", "删除自定义角色", AuditOperationType.DELETE, "admin.roles", "角色管理", "admin_custom_role"),
    ADMIN_CUSTOM_ROLE_EXECUTE("ADMIN_CUSTOM_ROLE_EXECUTE", "应用自定义角色变更", AuditOperationType.EXECUTE, "admin.roles", "角色管理", "admin_custom_role"),
    ADMIN_ROLE_ASSIGNMENT_VIEW("ADMIN_ROLE_ASSIGNMENT_VIEW", "查看角色授权", AuditOperationType.READ, "admin.roles", "角色管理", "admin_role_assignment"),
    ADMIN_DATASET_VIEW("ADMIN_DATASET_VIEW", "查看数据资产", AuditOperationType.READ, "admin.datasets", "资产视图", "admin_dataset"),
    ADMIN_SETTING_VIEW("ADMIN_SETTING_VIEW", "查看系统配置", AuditOperationType.READ, "admin.settings", "系统配置", "system_config"),
    ADMIN_AUTH_LOGIN("ADMIN_AUTH_LOGIN", "管理员登录", AuditOperationType.LOGIN, "admin.auth", "认证登录", "admin_keycloak_user"),
    ADMIN_AUTH_LOGOUT("ADMIN_AUTH_LOGOUT", "管理员登出", AuditOperationType.LOGOUT, "admin.auth", "认证登录", "admin_keycloak_user"),
    ADMIN_AUTH_REFRESH("ADMIN_AUTH_REFRESH", "刷新登录状态", AuditOperationType.REFRESH, "admin.auth", "认证登录", "admin_keycloak_user");

    private final String code;
    private final String defaultName;
    private final AuditOperationType type;
    private final String moduleKey;
    private final String moduleLabel;
    private final String targetTable;

    AdminAuditOperation(
        String code,
        String defaultName,
        AuditOperationType type,
        String moduleKey,
        String moduleLabel,
        String targetTable
    ) {
        this.code = code;
        this.defaultName = defaultName;
        this.type = type;
        this.moduleKey = moduleKey;
        this.moduleLabel = moduleLabel;
        this.targetTable = targetTable;
    }

    public String code() {
        return code;
    }

    public String defaultName() {
        return defaultName;
    }

    public AuditOperationType type() {
        return type;
    }

    public String moduleKey() {
        return moduleKey;
    }

    public String moduleLabel() {
        return moduleLabel;
    }

    public String targetTable() {
        return targetTable;
    }

    public static Optional<AdminAuditOperation> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        for (AdminAuditOperation op : values()) {
            if (op.code.equals(normalized)) {
                return Optional.of(op);
            }
        }
        return Optional.empty();
    }
}
