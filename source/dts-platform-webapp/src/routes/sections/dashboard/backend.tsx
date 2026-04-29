import type { RouteObject } from "react-router";
import { Navigate } from "react-router";
import { getMenus } from "@/store/menuStore";
import type { MenuMetaInfo, MenuTree } from "@/types/entity";
import { PermissionType } from "@/types/enum";
import { parseMenuMetadata } from "@/utils/menuTree";
import { convertFlatToTree } from "@/utils/tree";
import { DynamicMenuResolver, resolveDashboardComponentOverride } from "./dynamic-resolver";
import { Component } from "./utils";

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
	// Prefer explicit field, then fall back to metadata.externalLink
	if (metaInfo.externalLink) {
		props.src = metaInfo.externalLink?.toString?.() || "";
	}
	if (!props.src && (metaInfo as any)?.metadata) {
		const meta = parseMenuMetadata((metaInfo as any).metadata);
		if (meta) {
			const external =
				(meta as any).externalLink ??
				(meta as any).external_link ??
				(meta as any).url ??
				(meta as any).href ??
				(meta as any).link ??
				(meta as any).src;
			if (typeof external === "string") {
				props.src = String(external);
			}
		}
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
			const component = resolveDashboardComponentOverride(item.path) || item.component;

			routes.push({
				path: getRoutePath(item.path, parent?.path),
				element: Component(component, props),
			});
		}
	};

	for (const item of items) {
		processItem(item);
	}
	return routes;
};

// Provide dynamic fallback routes for key sections so menu-driven paths resolve before menus are baked into routes.
const buildDynamicFallbackRoutes = (): RouteObject[] => [
	{ path: "workbench", children: [{ path: "*", element: <DynamicMenuResolver base="/workbench" /> }] },
	{ path: "catalog", children: [{ path: "*", element: <DynamicMenuResolver base="/catalog" /> }] },
	{ path: "modeling", children: [{ path: "*", element: <DynamicMenuResolver base="/modeling" /> }] },
	{ path: "governance", children: [{ path: "*", element: <DynamicMenuResolver base="/governance" /> }] },
	{ path: "explore", children: [{ path: "*", element: <DynamicMenuResolver base="/explore" /> }] },
	{ path: "foundation", children: [{ path: "*", element: <DynamicMenuResolver base="/foundation" /> }] },
	{ path: "services", children: [{ path: "*", element: <DynamicMenuResolver base="/services" /> }] },
	{ path: "ops", children: [{ path: "*", element: <DynamicMenuResolver base="/ops" /> }] },
];

export function getBackendDashboardRoutes() {
	const menus = getMenus();
	const tree = hasChildren(menus) ? menus : convertFlatToTree(menus);
	const backendDashboardRoutes = convertToRoute(tree);
	// Always provide a static dashboard/workbench welcome route first, so it wins over wildcards
	backendDashboardRoutes.unshift({
		path: "dashboard",
		children: [
			{ index: true, element: <Navigate to="workbench" replace /> },
			{ path: "workbench", element: Component("/pages/workbench") },
		],
	});
	// Keep /workbench available even before menus load
	backendDashboardRoutes.unshift({ path: "workbench", element: Component("/pages/workbench") });
	// Add dynamic fallbacks for other sections so routes resolve even before menus are loaded
	backendDashboardRoutes.push(...buildDynamicFallbackRoutes());
	return backendDashboardRoutes;
}

const hasChildren = (items: MenuTree[]): boolean =>
	items.some((item) => Array.isArray(item.children) && item.children.length > 0);
