import { useMemo } from "react";
import { useUserPermissions, useUserRoles } from "@/store/userStore";

const MAINTAINER_ROLE_CODES = new Set([
	"ADMIN",
	"OP_ADMIN",
	"INST_DATA_OWNER",
	"DEPT_DATA_OWNER",
	"INST_LEADER",
	"DEPT_LEADER",
]);

const normalizeRole = (raw: unknown) =>
	String(raw || "")
		.trim()
		.toUpperCase()
		.replace(/^ROLE_/, "");

const normalizePermission = (raw: unknown) =>
	String(raw || "")
		.trim()
		.toLowerCase();

const hasMaintainerRole = (roles: unknown[]) =>
	roles.map(normalizeRole).some((role) => MAINTAINER_ROLE_CODES.has(role));

const hasModulePermission = (permissions: unknown[], moduleName: "governance" | "catalog") => {
	const normalized = new Set(permissions.map(normalizePermission));
	return (
		normalized.has(`${moduleName}.manage`) ||
		normalized.has(`${moduleName}:manage`) ||
		normalized.has(`manage.${moduleName}`) ||
		normalized.has(`${moduleName}.*`) ||
		normalized.has("*")
	);
};

const useModuleManageAccess = (moduleName: "governance" | "catalog") => {
	const roles = useUserRoles();
	const permissions = useUserPermissions();

	return useMemo(() => {
		if (hasMaintainerRole(roles || [])) return true;
		return hasModulePermission(permissions || [], moduleName);
	}, [roles, permissions, moduleName]);
};

export const useGovernanceManageAccess = () => useModuleManageAccess("governance");
export const useCatalogManageAccess = () => useModuleManageAccess("catalog");

export const useCatalogTagGovernanceAccess = () => {
	const roles = useUserRoles();
	return useMemo(() => hasMaintainerRole(roles || []), [roles]);
};
