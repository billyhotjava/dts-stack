import type { AdminRole } from "@/admin/types";
import { GLOBAL_CONFIG } from "@/global-config";

export interface AdminMenuItem {
	key: string;
	label: string;
	path: string;
	icon?: string;
}

const makeSysadminMenus = (): AdminMenuItem[] => {
    const base: AdminMenuItem[] = [
        { key: "org", label: "组织机构", path: "orgs", icon: "solar:buildings-outline" },
        { key: "users", label: "用户管理", path: "users", icon: "solar:users-group-outline" },
        { key: "roles", label: "角色管理", path: "roles", icon: "solar:shield-user-bold" },
        { key: "ops", label: "系统运维", path: "ops", icon: "solar:settings-outline" },
        { key: "mine", label: "我发起的变更", path: "drafts", icon: "solar:inbox-outline" },
    ];
    if (GLOBAL_CONFIG.enablePortalMenuMgmt) {
        base.splice(4, 0, { key: "portal-menus", label: "业务端菜单管理", path: "portal-menus", icon: "solar:list-bold" });
    }
    return base;
};

const authadminMenus: AdminMenuItem[] = [
	{ key: "approval", label: "审批中心", path: "approval", icon: "solar:verify-outline" },
	{ key: "rules", label: "审批规则", path: "approval?tab=rules", icon: "solar:book-outline" },
	{ key: "audit", label: "审计日志", path: "audit", icon: "solar:history-bold" },
];

const auditMenus: AdminMenuItem[] = [
	{ key: "audit", label: "审计日志", path: "audit", icon: "solar:history-bold" },
	{ key: "login", label: "登录/登出日志", path: "audit?tab=login", icon: "solar:door-open-bold" },
];

export function getMenusByRole(role: AdminRole): AdminMenuItem[] {
    switch (role) {
        case "SYSADMIN":
            return makeSysadminMenus();
        case "AUTHADMIN":
            return authadminMenus;
        case "AUDITADMIN":
            return auditMenus;
        default:
            return [];
    }
}
