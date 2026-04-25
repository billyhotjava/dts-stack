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

function hasMenuPath(menus: MenuTree[], path: string): boolean {
	return Boolean(findMenuByPath(menus, path) || findBestMenuMatch(menus, path));
}

export function hasSemanticModelingMenuAccess(menus: MenuTree[]): boolean {
	if (!Array.isArray(menus) || menus.length === 0) {
		return false;
	}
	return SEMANTIC_MODELING_MENU_PATHS.some((path) => hasMenuPath(menus, path));
}

export { SEMANTIC_MODELING_MENU_PATHS };
