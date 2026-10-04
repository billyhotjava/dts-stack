const QUALITY_MAINTAINER_ROLES = new Set([
	"ROLE_ADMIN",
	"ROLE_OP_ADMIN",
	"ROLE_INST_DATA_OWNER",
	"ROLE_DEPT_DATA_OWNER",
	"ROLE_INST_LEADER",
	"ROLE_DEPT_LEADER",
]);

export const normalizeQualityRoleCode = (role: unknown) => {
	const candidate =
		role && typeof role === "object"
			? ((role as { code?: unknown; name?: unknown }).code ?? (role as { name?: unknown }).name ?? role)
			: role;
	return String(candidate || "")
		.trim()
		.toUpperCase();
};

export const hasQualityMaintainerRole = (roles: readonly unknown[]) =>
	roles.some((role) => QUALITY_MAINTAINER_ROLES.has(normalizeQualityRoleCode(role)));

export const hasQualityTaskDeleteRole = (roles: readonly unknown[]) => hasQualityMaintainerRole(roles);
