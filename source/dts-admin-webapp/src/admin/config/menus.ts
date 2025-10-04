import { normalizeAdminRole, type AdminRole } from "@/admin/types";

export interface AdminMenuItem {
	key: string;
	label: string;
	path: string;
	icon?: string;
}

const sysadminMenus: AdminMenuItem[] = [
	{ key: "users", label: "用户管理", path: "/admin/users", icon: "local:ic-users" },
	{ key: "orgs", label: "组织管理", path: "/admin/orgs", icon: "local:ic-orgs" },
	{ key: "roles", label: "角色管理", path: "/admin/roles", icon: "local:ic-roles" },
	{ key: "approval", label: "任务审批", path: "/admin/approval", icon: "local:ic-approval" },
    { key: "mine", label: "我的申请", path: "/admin/my-changes", icon: "local:ic-changes" },
	{ key: "audit", label: "日志审计", path: "/admin/audit", icon: "local:ic-audit" },
	{ key: "ops", label: "系统运维", path: "/admin/ops", icon: "local:ic-setting" },
	{ key: "portal-menus", label: "菜单管理", path: "/admin/portal-menus", icon: "local:ic-menu" },
];

const authadminMenus: AdminMenuItem[] = [
	{ key: "tasks", label: "任务审批", path: "/admin/approval", icon: "local:ic-approval" },
	{ key: "audit", label: "日志审计", path: "/admin/audit", icon: "local:ic-audit" },
];

const auditMenus: AdminMenuItem[] = [{ key: "audit", label: "日志记录", path: "/admin/audit", icon: "local:ic-audit" }];

export function getMenusByRole(role: AdminRole | string | null | undefined): AdminMenuItem[] {
	const normalized = normalizeAdminRole(role);
	switch (normalized) {
		case "SYSADMIN":
		case "OPADMIN":
			return sysadminMenus;
		case "AUTHADMIN":
			return authadminMenus;
		case "AUDITADMIN":
			return auditMenus;
		default:
			return [];
	}
}
