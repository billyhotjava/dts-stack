import type { MenuTree } from "#/entity";
import { findBestMenuMatch, findMenuByPath } from "@/utils/menuTree";

const SEMANTIC_MODELING_MENU_PATHS = [
	"/bi/card/new",
	"/bi/questions/new",
	"/bi/questions",
	"/bi/explore",
	"/bi/virtual-datasets/new",
	"/bi/virtual-datasets",
];

const SEMANTIC_PROMOTION_ROLES = new Set([
	"ADMIN",
	"OP_ADMIN",
	"INST_DATA_OWNER",
	"DEPT_DATA_OWNER",
	"BI_DATA_ENGINEER",
]);

function normalizedRole(value: unknown): string {
	return String(value || "")
		.trim()
		.toUpperCase()
		.replace(/^ROLE_/, "");
}

function hasMenuPath(menus: MenuTree[], path: string): boolean {
	return Boolean(findMenuByPath(menus, path) || findBestMenuMatch(menus, path));
}

export function hasSemanticModelingMenuAccess(menus: MenuTree[]): boolean {
	if (!Array.isArray(menus) || menus.length === 0) {
		return false;
	}
	return SEMANTIC_MODELING_MENU_PATHS.some((path) => hasMenuPath(menus, path));
}

export function canPromoteSemanticModel(roles: unknown[]): boolean {
	return Array.isArray(roles) && roles.some((role) => SEMANTIC_PROMOTION_ROLES.has(normalizedRole(role)));
}

export { SEMANTIC_MODELING_MENU_PATHS, SEMANTIC_PROMOTION_ROLES };
