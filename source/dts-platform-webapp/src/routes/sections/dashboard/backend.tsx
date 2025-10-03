import type { RouteObject } from "react-router";
import { Navigate } from "react-router";
import type { MenuMetaInfo, MenuTree } from "@/types/entity";
import { PermissionType } from "@/types/enum";
import { convertFlatToTree } from "@/utils/tree";
import { Component } from "./utils";
import { getMenus } from "@/store/menuStore";
import useUserStore from "@/store/userStore";

/**
 * get route path from menu path and parent path
 * @param menuPath '/a/b/c'
 * @param parentPath '/a/b'
 * @returns '/c'
 *
 * @example
 * getRoutePath('/a/b/c', '/a/b') // '/c'
 */
const getRoutePath = (menuPath?: string, parentPath?: string) => {
	const menuPathArr = menuPath?.split("/").filter(Boolean) || [];
	const parentPathArr = parentPath?.split("/").filter(Boolean) || [];

	// remove parentPath items from menuPath
	const result = menuPathArr.slice(parentPathArr.length).join("/");
	return result;
};

/**
 * generate props for menu component
 * @param metaInfo
 * @returns
 */
const generateProps = (metaInfo: MenuMetaInfo) => {
	const props: any = {};
	if (metaInfo.externalLink) {
		props.src = metaInfo.externalLink?.toString() || "";
	}
	return props;
};

/**
 * convert menu to route
 * @param items
 * @param parent
 * @returns
 */
const convertToRoute = (items: MenuTree[], parent?: MenuTree): RouteObject[] => {
	const routes: RouteObject[] = [];

	const processItem = (item: MenuTree) => {
		// if group, process children
		if (item.type === PermissionType.GROUP) {
			for (const child of item.children || []) {
				processItem(child);
			}
		}

		// if catalogue, process Dhildren
		if (item.type === PermissionType.CATALOGUE) {
			const children = item.children || [];
			if (children.length > 0) {
				const firstChild = children[0];
				if (firstChild.path) {
					routes.push({
						path: getRoutePath(item.path, parent?.path),
						children: [
							{
								index: true,
								element: <Navigate to={getRoutePath(firstChild.path, item.path)} replace />,
							},
							...convertToRoute(children, item),
						],
					});
				}
			}
		}

		// if menu, create route
		if (item.type === PermissionType.MENU) {
			const props = generateProps(item);

			routes.push({
				path: getRoutePath(item.path, parent?.path),
				element: Component(item.component, props),
			});
		}
	};

	for (const item of items) {
		processItem(item);
	}
	return routes;
};

const ADMIN_ROLE_CODES = new Set(["ROLE_OP_ADMIN", "ROLE_SYS_ADMIN"]);

const hasAdminPrivileges = () => {
	const roles = useUserStore.getState().userInfo.roles || [];
	return roles.some((role: any) => {
		const value = typeof role === "string" ? role : role?.code;
		return value ? ADMIN_ROLE_CODES.has(value.toString().toUpperCase()) : false;
	});
};

const buildAdminManagementRoutes = (): RouteObject[] => [
	{
		path: "management",
		children: [
			{ index: true, element: <Navigate to="system/user" replace /> },
			{
				path: "system",
				children: [
					{ index: true, element: <Navigate to="user" replace /> },
					{ path: "permission", element: Component("/pages/management/system/permission") },
					{ path: "role", element: Component("/pages/management/system/role") },
					{ path: "group", element: Component("/pages/management/system/group") },
					{ path: "user", element: Component("/pages/management/system/user") },
					{ path: "user/:id", element: Component("/pages/management/system/user/detail") },
					{ path: "approval", element: Component("/pages/management/system/approval") },
					{ path: "auditlog", element: Component("/pages/management/system/auditlog") },
				],
			},
			{
				path: "user",
				children: [
					{ index: true, element: <Navigate to="profile" replace /> },
					{ path: "profile", element: Component("/pages/management/user/profile") },
				],
			},
		],
	},
];

export function getBackendDashboardRoutes() {
	const menus = getMenus();
	const tree = hasChildren(menus) ? menus : convertFlatToTree(menus);
	const backendDashboardRoutes = convertToRoute(tree);
	if (hasAdminPrivileges()) {
		backendDashboardRoutes.push(...buildAdminManagementRoutes());
	}
	return backendDashboardRoutes;
}

const hasChildren = (items: MenuTree[]): boolean => items.some((item) => Array.isArray(item.children) && item.children.length > 0);
