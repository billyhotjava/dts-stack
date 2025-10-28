package com.yuzhi.dts.admin.service.auditv2;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AuditButtonRegistry {

    private final Map<String, AuditButtonMetadata> registry;

    public AuditButtonRegistry() {
        Map<String, AuditButtonMetadata> map = new LinkedHashMap<>();
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.SYSTEM_CONFIG_SUBMIT,
                "system-admin",
                "系统管理",
                "CONFIG_SUBMIT",
                "提交系统配置变更",
                AuditOperationKind.CREATE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.SYSTEM_CONFIG_VIEW,
                "system-admin",
                "系统管理",
                "CONFIG_VIEW",
                "查看系统配置",
                AuditOperationKind.QUERY,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.DATA_SOURCE_REFRESH,
                "system-admin",
                "基础设施",
                "ADMIN_DATA_SOURCE_REFRESH",
                "刷新数据源注册信息",
                AuditOperationKind.EXECUTE,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.DATA_SOURCE_CREATE,
                "system-admin",
                "基础设施",
                "ADMIN_DATA_SOURCE_CREATE",
                "新增数据源",
                AuditOperationKind.CREATE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.DATA_SOURCE_DELETE,
                "system-admin",
                "基础设施",
                "ADMIN_DATA_SOURCE_DELETE",
                "删除数据源",
                AuditOperationKind.DELETE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.CHANGE_REQUEST_SUBMIT,
                "system-admin",
                "系统管理",
                "CHANGE_REQUEST_SUBMIT",
                "提交变更申请",
                AuditOperationKind.CREATE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.CHANGE_REQUEST_APPROVE,
                "system-admin",
                "系统管理",
                "CHANGE_REQUEST_APPROVE",
                "批准变更申请",
                AuditOperationKind.APPROVE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.CHANGE_REQUEST_REJECT,
                "system-admin",
                "系统管理",
                "CHANGE_REQUEST_REJECT",
                "驳回变更申请",
                AuditOperationKind.REJECT,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.CHANGE_REQUEST_VIEW,
                "system-admin",
                "系统管理",
                "CHANGE_REQUEST_VIEW",
                "查看变更详情",
                AuditOperationKind.QUERY,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.USER_LIST,
                "system-admin",
                "系统管理",
                "USER_LIST",
                "查看用户列表",
                AuditOperationKind.QUERY,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.USER_SEARCH,
                "system-admin",
                "系统管理",
                "USER_SEARCH",
                "搜索用户",
                AuditOperationKind.QUERY,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.USER_VIEW,
                "system-admin",
                "系统管理",
                "USER_VIEW",
                "查看用户详情",
                AuditOperationKind.QUERY,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.USER_CREATE,
                "system-admin",
                "系统管理",
                "USER_CREATE",
                "新增用户",
                AuditOperationKind.CREATE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.USER_UPDATE,
                "system-admin",
                "系统管理",
                "USER_UPDATE",
                "修改用户",
                AuditOperationKind.UPDATE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.USER_RESET_PASSWORD,
                "system-admin",
                "系统管理",
                "USER_RESET_PASSWORD",
                "重置用户密码",
                AuditOperationKind.UPDATE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.USER_ENABLE,
                "system-admin",
                "系统管理",
                "USER_ENABLE",
                "启用用户",
                AuditOperationKind.ENABLE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.USER_DISABLE,
                "system-admin",
                "系统管理",
                "USER_DISABLE",
                "禁用用户",
                AuditOperationKind.DISABLE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.USER_SET_PERSON_LEVEL,
                "system-admin",
                "系统管理",
                "USER_SET_PERSON_LEVEL",
                "调整人员密级",
                AuditOperationKind.UPDATE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.USER_ASSIGN_ROLES,
                "system-admin",
                "系统管理",
                "USER_ASSIGN_ROLES",
                "分配用户角色",
                AuditOperationKind.UPDATE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.USER_REMOVE_ROLES,
                "system-admin",
                "系统管理",
                "USER_REMOVE_ROLES",
                "回收用户角色",
                AuditOperationKind.UPDATE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.USER_GROUP_MEMBERSHIPS_VIEW,
                "system-admin",
                "系统管理",
                "USER_GROUP_MEMBERSHIPS_VIEW",
                "查看用户所属用户组",
                AuditOperationKind.QUERY,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.GROUP_LIST,
                "system-admin",
                "系统管理",
                "GROUP_LIST",
                "查看用户组列表",
                AuditOperationKind.QUERY,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.GROUP_VIEW,
                "system-admin",
                "系统管理",
                "GROUP_VIEW",
                "查看用户组详情",
                AuditOperationKind.QUERY,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.GROUP_CREATE,
                "system-admin",
                "系统管理",
                "GROUP_CREATE",
                "新增用户组",
                AuditOperationKind.CREATE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.GROUP_UPDATE,
                "system-admin",
                "系统管理",
                "GROUP_UPDATE",
                "修改用户组",
                AuditOperationKind.UPDATE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.GROUP_DELETE,
                "system-admin",
                "系统管理",
                "GROUP_DELETE",
                "删除用户组",
                AuditOperationKind.DELETE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.GROUP_MEMBERS_VIEW,
                "system-admin",
                "系统管理",
                "GROUP_MEMBERS_VIEW",
                "查看用户组成员",
                AuditOperationKind.QUERY,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.GROUP_USER_MEMBERS_VIEW,
                "system-admin",
                "系统管理",
                "GROUP_USER_MEMBERS_VIEW",
                "查看用户所属用户组",
                AuditOperationKind.QUERY,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.GROUP_ADD_MEMBER,
                "system-admin",
                "系统管理",
                "GROUP_ADD_MEMBER",
                "添加用户到用户组",
                AuditOperationKind.UPDATE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.GROUP_REMOVE_MEMBER,
                "system-admin",
                "系统管理",
                "GROUP_REMOVE_MEMBER",
                "从用户组移除用户",
                AuditOperationKind.UPDATE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.ROLE_PLATFORM_LIST,
                "system-admin",
                "系统管理",
                "ROLE_PLATFORM_LIST",
                "查看平台角色列表",
                AuditOperationKind.QUERY,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.ROLE_LIST,
                "system-admin",
                "系统管理",
                "ROLE_LIST",
                "查看角色列表",
                AuditOperationKind.QUERY,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.ROLE_VIEW,
                "system-admin",
                "系统管理",
                "ROLE_VIEW",
                "查看角色详情",
                AuditOperationKind.QUERY,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.ROLE_USERS_VIEW,
                "system-admin",
                "系统管理",
                "ROLE_USERS_VIEW",
                "查看角色用户列表",
                AuditOperationKind.QUERY,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.ROLE_CREATE,
                "system-admin",
                "系统管理",
                "ROLE_CREATE",
                "新增角色",
                AuditOperationKind.CREATE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.ROLE_UPDATE,
                "system-admin",
                "系统管理",
                "ROLE_UPDATE",
                "修改角色",
                AuditOperationKind.UPDATE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.ROLE_DELETE,
                "system-admin",
                "系统管理",
                "ROLE_DELETE",
                "删除角色",
                AuditOperationKind.DELETE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.APPROVAL_LIST,
                "system-admin",
                "系统管理",
                "APPROVAL_LIST",
                "查看审批请求列表",
                AuditOperationKind.QUERY,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.APPROVAL_VIEW,
                "system-admin",
                "系统管理",
                "APPROVAL_VIEW",
                "查看审批请求详情",
                AuditOperationKind.QUERY,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.APPROVAL_APPROVE,
                "system-admin",
                "系统管理",
                "APPROVAL_APPROVE",
                "批准审批请求",
                AuditOperationKind.APPROVE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.APPROVAL_REJECT,
                "system-admin",
                "系统管理",
                "APPROVAL_REJECT",
                "驳回审批请求",
                AuditOperationKind.REJECT,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.APPROVAL_DELAY,
                "system-admin",
                "系统管理",
                "APPROVAL_DELAY",
                "暂缓处理审批请求",
                AuditOperationKind.UPDATE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.AUTH_ADMIN_LOGIN,
                "system-admin",
                "系统管理",
                "AUTH_ADMIN_LOGIN",
                "系统端登录",
                AuditOperationKind.LOGIN,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.AUTH_ADMIN_LOGOUT,
                "system-admin",
                "系统管理",
                "AUTH_ADMIN_LOGOUT",
                "系统端登出",
                AuditOperationKind.LOGOUT,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.AUTH_ADMIN_REFRESH,
                "system-admin",
                "系统管理",
                "AUTH_ADMIN_REFRESH",
                "刷新系统端令牌",
                AuditOperationKind.UPDATE,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.AUTH_PLATFORM_LOGIN,
                "system-admin",
                "系统管理",
                "AUTH_PLATFORM_LOGIN",
                "业务端登录",
                AuditOperationKind.LOGIN,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.AUDIT_LOG_QUERY,
                "system-admin",
                "系统管理",
                "AUDIT_LOG_QUERY",
                "查询审计日志",
                AuditOperationKind.QUERY,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.AUDIT_LOG_EXPORT,
                "system-admin",
                "系统管理",
                "AUDIT_LOG_EXPORT",
                "导出审计日志",
                AuditOperationKind.EXPORT,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.PORTAL_MENU_VIEW,
                "system-admin",
                "系统管理",
                "PORTAL_MENU_VIEW",
                "查看门户菜单",
                AuditOperationKind.QUERY,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.PORTAL_MENU_CREATE,
                "system-admin",
                "系统管理",
                "PORTAL_MENU_CREATE",
                "新增门户菜单",
                AuditOperationKind.CREATE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.PORTAL_MENU_UPDATE,
                "system-admin",
                "系统管理",
                "PORTAL_MENU_UPDATE",
                "修改门户菜单",
                AuditOperationKind.UPDATE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.PORTAL_MENU_DELETE,
                "system-admin",
                "系统管理",
                "PORTAL_MENU_DISABLE",
                "禁用门户菜单",
                AuditOperationKind.DISABLE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.ORG_CREATE,
                "system-admin",
                "系统管理",
                "ORG_CREATE",
                "新增组织节点",
                AuditOperationKind.CREATE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.ORG_UPDATE,
                "system-admin",
                "系统管理",
                "ORG_UPDATE",
                "修改组织节点",
                AuditOperationKind.UPDATE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.ORG_DELETE,
                "system-admin",
                "系统管理",
                "ORG_DELETE",
                "删除组织节点",
                AuditOperationKind.DELETE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.ORG_SYNC,
                "system-admin",
                "系统管理",
                "ORG_SYNC",
                "同步组织结构",
                AuditOperationKind.EXECUTE,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.ORG_LIST,
                "system-admin",
                "系统管理",
                "ORG_LIST",
                "查看组织结构",
                AuditOperationKind.QUERY,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.DATASET_LIST,
                "system-admin",
                "系统管理",
                "DATASET_LIST",
                "查看数据集列表",
                AuditOperationKind.QUERY,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.ROLE_ASSIGNMENT_LIST,
                "system-admin",
                "系统管理",
                "ROLE_ASSIGNMENT_LIST",
                "查看角色指派列表",
                AuditOperationKind.QUERY,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.ROLE_ASSIGNMENT_CREATE,
                "system-admin",
                "系统管理",
                "ROLE_ASSIGNMENT_CREATE",
                "提交角色指派申请",
                AuditOperationKind.CREATE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.CUSTOM_ROLE_LIST,
                "system-admin",
                "系统管理",
                "CUSTOM_ROLE_LIST",
                "查看自定义角色列表",
                AuditOperationKind.QUERY,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.CUSTOM_ROLE_CREATE,
                "system-admin",
                "系统管理",
                "CUSTOM_ROLE_CREATE",
                "提交自定义角色申请",
                AuditOperationKind.CREATE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.PERMISSION_CATALOG_VIEW,
                "system-admin",
                "系统管理",
                "PERMISSION_CATALOG_VIEW",
                "查看权限目录",
                AuditOperationKind.QUERY,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.CHANGE_REQUEST_LIST,
                "system-admin",
                "系统管理",
                "CHANGE_REQUEST_LIST",
                "查看变更请求列表",
                AuditOperationKind.QUERY,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.CHANGE_REQUEST_LIST_MINE,
                "system-admin",
                "系统管理",
                "CHANGE_REQUEST_LIST_MINE",
                "查看我的变更请求",
                AuditOperationKind.QUERY,
                true
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.CHANGE_REQUEST_DRAFT,
                "system-admin",
                "系统管理",
                "CHANGE_REQUEST_DRAFT",
                "创建变更草稿",
                AuditOperationKind.CREATE,
                false
            )
        );
        register(
            map,
            new AuditButtonMetadata(
                ButtonCodes.CHANGE_REQUEST_PURGE,
                "system-admin",
                "系统管理",
                "CHANGE_REQUEST_PURGE",
                "清理历史审批/变更数据",
                AuditOperationKind.DELETE,
                true
            )
        );
        this.registry = Collections.unmodifiableMap(map);
    }

    private void register(Map<String, AuditButtonMetadata> holder, AuditButtonMetadata metadata) {
        holder.put(metadata.buttonCode(), metadata);
    }

    public Optional<AuditButtonMetadata> resolve(String buttonCode) {
        if (buttonCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(registry.get(buttonCode.trim().toUpperCase()));
    }

    public Map<String, AuditButtonMetadata> all() {
        return registry;
    }
}
