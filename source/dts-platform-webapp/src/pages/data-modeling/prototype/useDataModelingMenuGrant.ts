import { useMemo } from "react";
import { useLocation } from "react-router";
import type { MenuTree } from "#/entity";
import { useMenuStore } from "@/store/menuStore";
import { findMenuByPath } from "@/utils/menuTree";
import { useModelingAuthorization } from "./useModelingAccess";

export const hasDataModelingMenuGrant = (menus: MenuTree[], pathname: string): boolean =>
	Boolean(findMenuByPath(Array.isArray(menus) ? menus : [], pathname));

/**
 * Data-modeling actions follow the portal menu grant returned by dts-admin.
 * The dashboard route guard remains the navigation boundary; API endpoints
 * still perform the final server-side authorization check.
 */
export function useDataModelingMenuGrant(): boolean {
	const authorization = useModelingAuthorization();
	const menus = useMenuStore((state) => state.menus);
	const { pathname } = useLocation();
	return useMemo(
		() => !authorization.isError && authorization.data?.canModel === true && hasDataModelingMenuGrant(menus, pathname),
		[menus, pathname, authorization.data, authorization.isError],
	);
}
