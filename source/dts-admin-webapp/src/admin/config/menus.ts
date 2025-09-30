import { normalizeAdminRole, type AdminRole } from "@/admin/types";

export interface AdminMenuItem {
	key: string;
	label: string;
	path: string;
	icon?: string;
}

const sysadminMenus: AdminMenuItem[] = [
	{ key: "org", label: "组织机构", path: "orgs", icon: "solar:buildings-outline" },
	{ key: "users", label: "用户管理", path: "users", icon: "solar:users-group-outline" },
	{ key: "roles", label: "角色管理", path: "roles", icon: "solar:shield-user-bold" },
	{ key: "approval", label: "任务审批", path: "approval", icon: "solar:verify-outline" },
	{ key: "ops", label: "系统运维", path: "ops", icon: "solar:settings-outline" },
	{ key: "portal-menus", label: "业务端菜单管理", path: "portal-menus", icon: "solar:list-bold" },
	{ key: "mine", label: "我发起的变更", path: "drafts", icon: "solar:inbox-outline" },
];

const authadminMenus: AdminMenuItem[] = [
	{ key: "tasks", label: "任务审批", path: "approval", icon: "solar:verify-outline" },
	{ key: "audit", label: "日志审计", path: "audit", icon: "solar:history-bold" },
];

const auditMenus: AdminMenuItem[] = [{ key: "audit", label: "日志记录", path: "audit", icon: "solar:history-bold" }];

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
