import type { ReactNode } from "react";
import { Navigate } from "react-router";
import type { MenuTree } from "#/entity";
import { LineLoading } from "@/components/loading";
import { GLOBAL_CONFIG } from "@/global-config";
import { useMenuStore } from "@/store/menuStore";
import { findMenuByPath, firstAccessibleMenuPath, hasMenuComponent, isContainerMenu } from "@/utils/menuTree";

export function permittedLandingPath(menus: MenuTree[], preferred: string): string | null {
	const menu = findMenuByPath(menus, preferred);
	if (menu && (hasMenuComponent(menu) || !isContainerMenu(menu))) return preferred;
	return firstAccessibleMenuPath(menus);
}

export function AuthorizedWorkbenchRoute({ children }: { children?: ReactNode }) {
	const { menus, loaded } = useMenuStore();
	if (!loaded) return <LineLoading />;
	const workbench = ["/workbench", "/dashboard/workbench"].some((path) => {
		const menu = findMenuByPath(menus, path);
		return menu && (hasMenuComponent(menu) || !isContainerMenu(menu));
	});
	if (children && workbench) return <>{children}</>;
	const target = permittedLandingPath(menus, GLOBAL_CONFIG.defaultRoute);
	if (target) return <Navigate to={target} replace />;
	return (
		<div role="status" className="p-6">
			当前账号尚未分配可访问的菜单，请联系管理员授权。
		</div>
	);
}
