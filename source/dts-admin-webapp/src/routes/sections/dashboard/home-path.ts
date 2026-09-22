import { GLOBAL_CONFIG } from "@/global-config";

/**
 * 按三员职责选择登录/拒绝恢复落点（F11-UI-001 约束集中点）。
 *
 * 审计员 → 日志审计；授权管理员 → 任务审批；系统管理员 → 我的申请；
 * 其他 → 全局默认路由。调用方不得把本函数结果改写为固定首页。
 */
export const resolveHomePathForRoles = (roles: string[]): string => {
	const normalized = (roles || []).map((role) =>
		String(role || "")
			.trim()
			.toUpperCase(),
	);
	const hasRole = (needle: string) => normalized.includes(needle);
	if (
		hasRole("AUDITADMIN") ||
		hasRole("ROLE_SECURITY_AUDITOR") ||
		hasRole("SECURITYAUDITOR") ||
		hasRole("ROLE_AUDITOR_ADMIN") ||
		hasRole("ROLE_AUDIT_ADMIN")
	) {
		return "/admin/audit";
	}
	if (hasRole("AUTHADMIN") || hasRole("ROLE_AUTH_ADMIN")) {
		return "/admin/approval";
	}
	if (hasRole("SYSADMIN") || hasRole("ROLE_SYS_ADMIN")) {
		return "/admin/my-changes";
	}
	return GLOBAL_CONFIG.defaultRoute;
};
