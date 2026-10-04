import type { ReactNode } from "react";
import { Navigate } from "react-router";
import type { MenuTree } from "#/entity";
import { LineLoading } from "@/components/loading";
import { GLOBAL_CONFIG } from "@/global-config";
import { useMenuStore } from "@/store/menuStore";
import {
	firstAccessibleMenuPath,
	hasMenuComponent,
	isContainerMenu,
	isMenuDeleted,
	isMenuDisabled,
	isMenuHidden,
	parseMenuMetadata,
	resolveMenuPath,
} from "@/utils/menuTree";

function hasAccessiblePage(menus: MenuTree[], target: string, parentPath?: string): boolean {
	return menus.some((menu) => {
		const meta = parseMenuMetadata(menu.metadata);
		if (isMenuDeleted(menu) || isMenuDisabled(menu, meta) || isMenuHidden(menu, meta)) return false;
		const path = resolveMenuPath(menu, meta, parentPath);
		if (path === target && (hasMenuComponent(menu) || !isContainerMenu(menu))) return true;
		return hasAccessiblePage(menu.children || [], target, path);
	});
}

export function permittedLandingPath(menus: MenuTree[], preferred: string): string | null {
	if (hasAccessiblePage(menus, preferred)) return preferred;
	return firstAccessibleMenuPath(menus);
}

export function AuthorizedWorkbenchRoute({ children }: { children?: ReactNode }) {
	const { menus, loaded } = useMenuStore();
	if (!loaded) return <LineLoading />;
	const workbench = ["/workbench", "/dashboard/workbench"].some((path) => hasAccessiblePage(menus, path));
	if (children && workbench) return <>{children}</>;
	const target = permittedLandingPath(menus, GLOBAL_CONFIG.defaultRoute);
	if (target) return <Navigate to={target} replace />;
	return (
		<div role="status" className="p-6">
			当前账号尚未分配可访问的菜单，请联系管理员授权。
		</div>
	);
}
